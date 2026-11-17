#include <jni.h>
#include <llama.h>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <algorithm>

static llama_model * model = nullptr;
static llama_context * context = nullptr;
static std::mutex guard;
static std::atomic<bool> cancelled{false};
static bool abort_generation(void *) { return cancelled.load(); }
static void clear_model() { if(context)llama_free(context); if(model)llama_model_free(model); context=nullptr;model=nullptr; }
static std::string utf8(JNIEnv * env,jstring value) {
    if(!value)return "";
    const char * chars=env->GetStringUTFChars(value,nullptr);
    std::string out(chars);env->ReleaseStringUTFChars(value,chars);return out;
}
static jbyteArray bytes(JNIEnv * env,const std::string & value) {
    auto out=env->NewByteArray(static_cast<jsize>(value.size()));
    env->SetByteArrayRegion(out,0,static_cast<jsize>(value.size()),reinterpret_cast<const jbyte *>(value.data()));return out;
}
extern "C" JNIEXPORT jstring JNICALL Java_lv_putnik_survivalkit_NativeLlama_load(JNIEnv * env,jobject,jstring path) {
    std::lock_guard<std::mutex> lock(guard);cancelled=false;clear_model();llama_backend_init();
    auto params=llama_model_default_params();params.n_gpu_layers=0;params.load_mode=LLAMA_LOAD_MODE_MMAP;
    params.progress_callback=[](float,void *) { return !cancelled.load(); };
    model=llama_model_load_from_file(utf8(env,path).c_str(),params);
    if(!model)return env->NewStringUTF("Model could not be loaded");
    auto cp=llama_context_default_params();cp.n_ctx=2048;cp.n_batch=256;cp.n_ubatch=128;
    cp.n_threads=std::min(4u,std::max(1u,std::thread::hardware_concurrency()));cp.n_threads_batch=cp.n_threads;
    cp.abort_callback=abort_generation;
    context=llama_init_from_model(model,cp);
    if(!context){clear_model();return env->NewStringUTF("Not enough memory for model context");}
    if(cancelled){clear_model();return env->NewStringUTF("Model loading cancelled");}
    return env->NewStringUTF("");
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_lv_putnik_survivalkit_NativeLlama_generate(JNIEnv * env,jobject,jstring content) {
    std::lock_guard<std::mutex> lock(guard);cancelled=false;
    if(!model||!context)return bytes(env,"");
    const std::string text=utf8(env,content);
    // Qwen ChatML; the closed thinking block prevents unbounded private reasoning before the answer.
    const std::string prompt="<|im_start|>system\nОтвечай кратко на русском. Следуй правилам и памятке в запросе.<|im_end|>\n<|im_start|>user\n"+text+"<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
    const auto vocab=llama_model_get_vocab(model);
    int count=-llama_tokenize(vocab,prompt.c_str(),static_cast<int>(prompt.size()),nullptr,0,true,true);
    if(count<=0||count>1600)return bytes(env,"");
    std::vector<llama_token> tokens(count);
    if(llama_tokenize(vocab,prompt.c_str(),static_cast<int>(prompt.size()),tokens.data(),count,true,true)<0)return bytes(env,"");
    llama_memory_clear(llama_get_memory(context),true);
    for(int start=0;start<count;start+=256) {
        if(cancelled)return bytes(env,"");
        auto batch=llama_batch_get_one(tokens.data()+start,std::min(256,count-start));
        if(llama_decode(context,batch)!=0)return bytes(env,"");
    }
    auto sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.9f,1));
    llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    std::string answer;
    for(int i=0;i<300 && !cancelled;i++) {
        llama_token token=llama_sampler_sample(sampler,context,-1);
        if(llama_vocab_is_eog(vocab,token))break;
        char buffer[512];int n=llama_token_to_piece(vocab,token,buffer,sizeof(buffer),0,false);
        if(n>0)answer.append(buffer,n);
        auto batch=llama_batch_get_one(&token,1);if(llama_decode(context,batch)!=0)break;
    }
    llama_sampler_free(sampler);if(cancelled)return bytes(env,"");return bytes(env,answer);
}
extern "C" JNIEXPORT void JNICALL Java_lv_putnik_survivalkit_NativeLlama_cancel(JNIEnv *,jobject) { cancelled=true; }
extern "C" JNIEXPORT void JNICALL Java_lv_putnik_survivalkit_NativeLlama_unload(JNIEnv *,jobject) { cancelled=true;std::lock_guard<std::mutex> lock(guard);clear_model(); }
