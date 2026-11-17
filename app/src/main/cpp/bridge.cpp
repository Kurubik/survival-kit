#include <jni.h>
#include <llama.h>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <algorithm>
#include <cstdint>

static llama_model * model = nullptr;
static llama_context * context = nullptr;
static std::mutex guard;
static std::atomic<int64_t> epoch{0};
static std::atomic<int64_t> active_request{0};
static bool abort_generation(void *) { return epoch.load()!=active_request.load(); }
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
extern "C" JNIEXPORT jlong JNICALL Java_lv_putnik_survivalkit_NativeLlama_startRequest(JNIEnv *,jobject) { return ++epoch; }
extern "C" JNIEXPORT jboolean JNICALL Java_lv_putnik_survivalkit_NativeLlama_isLoaded(JNIEnv *,jobject) { std::lock_guard<std::mutex> lock(guard);return model&&context; }
extern "C" JNIEXPORT jstring JNICALL Java_lv_putnik_survivalkit_NativeLlama_load(JNIEnv * env,jobject,jstring path,jlong request) {
    std::lock_guard<std::mutex> lock(guard);
    if(epoch.load()!=request)return env->NewStringUTF("Request cancelled");
    active_request=request;
    if(model&&context)return env->NewStringUTF("");
    clear_model();llama_backend_init();
    auto params=llama_model_default_params();params.n_gpu_layers=0;params.load_mode=LLAMA_LOAD_MODE_MMAP;
    params.progress_callback=[](float,void *) { return !abort_generation(nullptr); };
    model=llama_model_load_from_file(utf8(env,path).c_str(),params);
    if(!model)return env->NewStringUTF("Model could not be loaded");
    auto cp=llama_context_default_params();cp.n_ctx=4096;cp.n_batch=256;cp.n_ubatch=128;
    cp.n_threads=std::min(4u,std::max(1u,std::thread::hardware_concurrency()));cp.n_threads_batch=cp.n_threads;
    cp.abort_callback=abort_generation;
    context=llama_init_from_model(model,cp);
    if(!context){clear_model();return env->NewStringUTF("Not enough memory for model context");}
    if(abort_generation(nullptr)){clear_model();return env->NewStringUTF("Model loading cancelled");}
    return env->NewStringUTF("");
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_lv_putnik_survivalkit_NativeLlama_generate(JNIEnv * env,jobject,jbyteArray content,jobject listener,jlong request) {
    std::lock_guard<std::mutex> lock(guard);
    if(epoch.load()!=request)return bytes(env,"");
    active_request=request;
    if(!model||!context)return bytes(env,"");
    std::string prompt(static_cast<size_t>(env->GetArrayLength(content)),'\0');
    env->GetByteArrayRegion(content,0,static_cast<jsize>(prompt.size()),reinterpret_cast<jbyte *>(prompt.data()));
    const auto vocab=llama_model_get_vocab(model);
    int count=-llama_tokenize(vocab,prompt.c_str(),static_cast<int>(prompt.size()),nullptr,0,true,true);
    // Keep the system instruction and newest question; discard whole old exchanges.
    const std::string user_marker="<|im_start|>user\n";
    while(count>3500) {
        const auto first=prompt.find(user_marker);
        const auto next=first==std::string::npos?std::string::npos:prompt.find(user_marker,first+user_marker.size());
        if(next==std::string::npos)break;
        prompt.erase(first,next-first);
        count=-llama_tokenize(vocab,prompt.c_str(),static_cast<int>(prompt.size()),nullptr,0,true,true);
    }
    if(count<=0||count>3500)return bytes(env,"");
    std::vector<llama_token> tokens(count);
    if(llama_tokenize(vocab,prompt.c_str(),static_cast<int>(prompt.size()),tokens.data(),count,true,true)<0)return bytes(env,"");
    llama_memory_clear(llama_get_memory(context),true);
    for(int start=0;start<count;start+=256) {
        if(abort_generation(nullptr))return bytes(env,"");
        auto batch=llama_batch_get_one(tokens.data()+start,std::min(256,count-start));
        if(llama_decode(context,batch)!=0)return bytes(env,"");
    }
    auto sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.9f,1));
    llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    jmethodID on_text=nullptr;
    if(listener) {
        auto cls=env->GetObjectClass(listener);on_text=env->GetMethodID(cls,"onText","([B)V");env->DeleteLocalRef(cls);
        if(env->ExceptionCheck()){llama_sampler_free(sampler);return nullptr;}
    }
    auto emit=[&](const std::string & value) {
        if(!listener)return true;
        auto chunk=bytes(env,value);
        if(!chunk)return false;
        env->CallVoidMethod(listener,on_text,chunk);env->DeleteLocalRef(chunk);
        return !env->ExceptionCheck();
    };
    std::string answer;
    bool callback_ok=true;
    for(int i=0;i<384 && !abort_generation(nullptr);i++) {
        llama_token token=llama_sampler_sample(sampler,context,-1);
        if(llama_vocab_is_eog(vocab,token))break;
        char buffer[512];int n=llama_token_to_piece(vocab,token,buffer,sizeof(buffer),0,false);
        if(n>0)answer.append(buffer,n);
        if(i%8==7&&!emit(answer)){callback_ok=false;break;}
        auto batch=llama_batch_get_one(&token,1);if(llama_decode(context,batch)!=0)break;
    }
    llama_sampler_free(sampler);
    if(!callback_ok)return nullptr;
    if(abort_generation(nullptr))return bytes(env,"");
    if(!emit(answer))return nullptr;
    return bytes(env,answer);
}
extern "C" JNIEXPORT void JNICALL Java_lv_putnik_survivalkit_NativeLlama_cancel(JNIEnv *,jobject) { ++epoch; }
extern "C" JNIEXPORT void JNICALL Java_lv_putnik_survivalkit_NativeLlama_unload(JNIEnv *,jobject,jlong request) { std::lock_guard<std::mutex> lock(guard);if(epoch.load()==request)clear_model(); }
