@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package lv.putnik.survivalkit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import lv.putnik.core.*
import java.util.Locale
import java.text.SimpleDateFormat

private val Coal=Color(0xFF161A17)
private val Ivory=Color(0xFFE9DFC9)
private val Brass=Color(0xFFBC9A5D)
private val Moss=Color(0xFF65754F)
private val Paper=Color(0xFFE6D8BC)
private val Ink=Color(0xFF2F2B23)
private val Rust=Color(0xFF883D2C)
private val Heading=FontFamily(Font(R.font.heading))
private val Body=FontFamily(Font(R.font.body))

class MainActivity: ComponentActivity() {
    private val model: PutnikModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent { PutnikApp(model) }
    }
    override fun onStop() { model.pause();super.onStop() }
}

@Composable fun PutnikApp(vm: PutnikModel) {
    val context=LocalContext.current
    val scheme=if(vm.daylight) lightColorScheme(primary=Moss,secondary=Brass,background=Paper,surface=Ivory,onSurface=Ink,onBackground=Ink)
        else darkColorScheme(primary=Brass,secondary=Moss,background=Coal,surface=Color(0xFF24291F),onSurface=Ivory,onBackground=Ivory)
    MaterialTheme(colorScheme=scheme,typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontFamily=Body,fontSize=16.sp,lineHeight=24.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontFamily=Body,fontSize=14.sp,lineHeight=21.sp),titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=Heading,fontSize=30.sp,fontWeight=FontWeight.SemiBold),labelLarge=androidx.compose.ui.text.TextStyle(fontFamily=Body,fontSize=14.sp,fontWeight=FontWeight.Medium))) {
        val tabs=listOf(Page.CAMP to "Лагерь",Page.CHAT to "Спутник",Page.MAP to "Карта",Page.PACK to "Рюкзак")
        BackHandlerIfNeeded(vm)
        Scaffold(containerColor=scheme.background,topBar={
            TopAppBar(title={ Text(when(vm.page){Page.CAMP->"ПУТНИК";Page.CHAT->"Спутник";Page.MAP->"Карта";Page.PACK->"Рюкзак";Page.LIBRARY->"Памятки";Page.GUIDE->"Памятка";Page.HELP->"Нужна помощь";Page.SETTINGS->"Настройки"},fontFamily=Heading,fontSize=30.sp,fontWeight=FontWeight.SemiBold) },
                navigationIcon={if(vm.page !in tabs.map {it.first})IconButton(onClick={vm.page=Page.CAMP}) { Icon(Icons.Outlined.ArrowBack,"Назад") }},
                actions={ if(vm.page!=Page.HELP)TextButton(onClick={vm.page=Page.HELP},modifier=Modifier.testTag("help")) { Icon(Icons.Outlined.HelpOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Помощь") };IconButton(onClick={vm.page=Page.SETTINGS}) { Icon(Icons.Outlined.Settings,"Настройки") } },
                colors=TopAppBarDefaults.topAppBarColors(containerColor=scheme.background,titleContentColor=scheme.onBackground,actionIconContentColor=Brass))
        },bottomBar={
            NavigationBar(containerColor=if(vm.daylight)Ivory else Color(0xFF1C201A),tonalElevation=0.dp) {
                tabs.forEach { (page,label)->NavigationBarItem(selected=vm.page==page,onClick={vm.page=page},icon={ Icon(when(page){Page.CAMP->Icons.Outlined.Landscape;Page.CHAT->Icons.Outlined.ChatBubbleOutline;Page.MAP->Icons.Outlined.Map;else->Icons.Outlined.Backpack},null) },label={Text(label,fontFamily=Body,fontSize=12.sp)},modifier=Modifier.testTag("tab-${page.name}"),colors=NavigationBarItemDefaults.colors(selectedIconColor=Brass,selectedTextColor=Brass,indicatorColor=Moss.copy(alpha=.28f))) }
            }
        }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                when(vm.page) {
                    Page.CAMP->CampScreen(vm)
                    Page.CHAT->ChatScreen(vm)
                    Page.LIBRARY->LibraryScreen(vm)
                    Page.GUIDE->vm.selectedGuide?.let { GuideScreen(vm,it) }
                    Page.PACK->PackScreen(vm)
                    Page.MAP->MapScreen(vm)
                    Page.HELP->HelpScreen(vm)
                    Page.SETTINGS->SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable private fun BackHandlerIfNeeded(vm: PutnikModel) {
    androidx.activity.compose.BackHandler(enabled=vm.page!=Page.CAMP) { vm.page=Page.CAMP }
}

@Composable private fun PaperPanel(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Paper).border(1.dp,Brass.copy(alpha=.7f),RoundedCornerShape(12.dp)).drawBehind {
        for(i in 0..90) {
            val x=((i*173)%997)/997f*size.width;val y=((i*317)%991)/991f*size.height
            drawCircle(Color(0xFF786846).copy(alpha=.055f),1.2f,androidx.compose.ui.geometry.Offset(x,y))
        }
    }.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(primary=Moss,onSurface=Ink,onSurfaceVariant=Ink,surface=Paper,onPrimary=Ivory)) {
            CompositionLocalProvider(LocalContentColor provides Ink,content={content()})
        }
    }
}

@Composable private fun SectionTitle(text: String, modifier: Modifier=Modifier) { Text(text,modifier,fontFamily=Heading,fontSize=29.sp,fontWeight=FontWeight.SemiBold,lineHeight=32.sp) }
@Composable private fun Action(label: String, icon: ImageVector, modifier: Modifier=Modifier, enabled: Boolean=true, onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=52.dp),shape=RoundedCornerShape(10.dp),colors=ButtonDefaults.buttonColors(containerColor=Moss,contentColor=Ivory)) { Icon(icon,null,Modifier.size(21.dp));Spacer(Modifier.width(9.dp));Text(label,fontFamily=Body,fontSize=15.sp) }
}
@Composable private fun Badge(label: String) { Text(label,Modifier.clip(RoundedCornerShape(7.dp)).background(Moss.copy(alpha=.35f)).border(1.dp,Brass.copy(alpha=.45f),RoundedCornerShape(7.dp)).padding(horizontal=11.dp,vertical=5.dp),fontFamily=Body,fontSize=11.sp,color=Ivory) }

@Composable private fun CampScreen(vm: PutnikModel) {
    LazyColumn(Modifier.fillMaxSize().testTag("camp"),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(190.dp)) {
                Image(painterResource(R.drawable.camp),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Coal.copy(alpha=.05f),Coal.copy(alpha=.65f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Твой походный спутник",color=Ivory,fontFamily=Heading,fontSize=28.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Badge("ЛАТВИЯ И БАЛТИЯ");Badge("10 ПАМЯТОК ОФЛАЙН") }
                }
            }
        }
        item { SectionTitle("Что нужно сейчас?",Modifier.padding(horizontal=18.dp)) }
        items(listOf(listOf("fire","water"),listOf("shelter","first-aid"))) { row ->
            Row(Modifier.padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach { id ->
                    val guide=GuideLibrary.byId(id)!!
                    PaperPanel(Modifier.weight(1f).heightIn(min=108.dp).clickable {vm.openGuide(guide)}.testTag("topic-$id")) {
                        Icon(guideIcon(id),null,Modifier.size(33.dp),tint=if(id=="first-aid")Rust else Ink)
                        Text(if(id=="first-aid")"Аптечка" else guide.category,fontFamily=Heading,fontWeight=FontWeight.SemiBold,fontSize=25.sp)
                    }
                }
            }
        }
        item {
            Button(onClick={vm.page=Page.CHAT},modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp).heightIn(min=58.dp).testTag("ask-companion"),shape=RoundedCornerShape(10.dp),colors=ButtonDefaults.buttonColors(containerColor=Brass,contentColor=Ink)) {
                Icon(Icons.Outlined.Mic,null);Spacer(Modifier.width(10.dp));Text("Спросить спутника",fontFamily=Heading,fontSize=25.sp,fontWeight=FontWeight.SemiBold)
            }
        }
        item {
            PaperPanel(Modifier.fillMaxWidth().padding(horizontal=18.dp).clickable{vm.page=Page.LIBRARY}) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.MenuBook,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text("Открыть справочник",fontFamily=Heading,fontSize=25.sp);Text("Маршрут, тепло, вода и снаряжение",fontSize=12.sp) };Icon(Icons.Outlined.ChevronRight,null) }
            }
        }
        item { Text("Перед выходом проверь карту и голос в рюкзаке.",Modifier.padding(horizontal=20.dp),fontSize=12.sp,color=if(vm.daylight)Ink else Ivory.copy(alpha=.7f)) }
    }
}

private fun guideIcon(id: String): ImageVector = when(id) { "fire"->Icons.Outlined.LocalFireDepartment;"water"->Icons.Outlined.WaterDrop;"shelter"->Icons.Outlined.Cottage;"first-aid","ticks"->Icons.Outlined.MedicalServices;"lost","bog"->Icons.Outlined.Explore;"pack"->Icons.Outlined.Backpack;"weather"->Icons.Outlined.Cloud;else->Icons.Outlined.Thermostat }

@Composable private fun ChatScreen(vm: PutnikModel) {
    val context=LocalContext.current
    var question by rememberSaveableText()
    LaunchedEffect(vm.recognizedDraft) { if(vm.recognizedDraft.isNotBlank()){question=vm.recognizedDraft;vm.recognizedDraft=""} }
    val list=rememberLazyListState()
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted)vm.voice.listen() else vm.voiceStatus="Можно написать вопрос — микрофон не обязателен" }
    LaunchedEffect(vm.messages.size,vm.thinking) { if(vm.messages.isNotEmpty())list.animateScrollToItem(vm.messages.lastIndex) }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.padding(bottom=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Badge(vm.personality.label);Badge(if(vm.modelReady)"ЛОКАЛЬНЫЙ AI" else "ПО ПАМЯТКАМ") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("messages"),state=list,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)) {
            itemsIndexed(vm.messages) { _,message ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(message.user)Arrangement.End else Arrangement.Start) {
                    PaperPanel(Modifier.fillMaxWidth(if(message.user).91f else 1f)) {
                        Text(if(message.user)"Ты" else "Спутник",fontSize=11.sp,color=Moss,fontWeight=FontWeight.Bold)
                        Text(message.text,fontFamily=Body,fontSize=16.sp,lineHeight=24.sp)
                        if(!message.user) Row(verticalAlignment=Alignment.CenterVertically) {
                            message.guideId?.let { id -> TextButton(onClick={GuideLibrary.byId(id)?.let(vm::openGuide)}) { Icon(Icons.Outlined.MenuBook,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("Памятка и источники",fontSize=12.sp) } }
                            Spacer(Modifier.weight(1f));IconButton(onClick={vm.voice.speak(message.text)}) { Icon(Icons.Outlined.VolumeUp,"Прочитать ответ",tint=Moss) }
                        }
                    }
                }
            }
            if(vm.thinking)item { Row(verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp),color=Brass,strokeWidth=2.dp);Spacer(Modifier.width(10.dp));Text("Спутник думает на устройстве…",fontSize=12.sp);IconButton(onClick=vm::cancelGeneration) { Icon(Icons.Outlined.Close,"Остановить AI") } } }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
            vm.choices.forEach { text -> AssistChip(onClick={vm.ask(text)},label={Text(text,fontSize=12.sp)},modifier=Modifier.heightIn(min=40.dp)) }
        }
        Row(Modifier.imePadding().padding(top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=question,onValueChange={question=it.take(1500)},modifier=Modifier.weight(1f).testTag("question"),placeholder={Text("Напиши вопрос…",fontSize=14.sp)},maxLines=3,shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={vm.ask(question);question=""}))
            FilledIconButton(onClick={if(question.isNotBlank()){vm.ask(question);question=""}else if(vm.listening){vm.voice.stopListening();vm.listening=false;vm.voiceStatus="Запись остановлена"}else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)vm.voice.listen() else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.size(56.dp).testTag("send"),colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(vm.listening)Rust else Brass,contentColor=Ink)) { Icon(if(question.isNotBlank())Icons.Outlined.Send else if(vm.listening)Icons.Outlined.Stop else Icons.Outlined.Mic,if(question.isNotBlank())"Отправить" else if(vm.listening)"Остановить запись" else "Говорить") }
        }
        Text(vm.voiceStatus,Modifier.padding(bottom=6.dp),fontSize=10.sp,lineHeight=14.sp)
    }
}

@Composable private fun rememberSaveableText() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

@Composable private fun LibraryScreen(vm: PutnikModel) {
    var query by rememberSaveableText();var savedOnly by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("search"),label={Text("Поиск по русским памяткам")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true) }
        item { FilterChip(selected=savedOnly,onClick={savedOnly=!savedOnly},label={Text("Сохранённое")},leadingIcon={Icon(Icons.Outlined.BookmarkBorder,null,Modifier.size(16.dp))}) }
        val found=GuideLibrary.search(query).filter { !savedOnly||it.id in vm.bookmarks }
        if(found.isEmpty())item { Text("Подходящих памяток нет. Попробуй другое слово.") }
        items(found,key={it.id}) { guide ->
            PaperPanel(Modifier.fillMaxWidth().clickable {vm.openGuide(guide)}) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(guideIcon(guide.id),null,Modifier.size(28.dp),tint=Moss);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text(guide.title,fontFamily=Heading,fontSize=27.sp);Text(guide.summary,fontSize=12.sp) };Icon(Icons.Outlined.ChevronRight,null) }
            }
        }
    }
}

@Composable private fun GuideScreen(vm: PutnikModel,guide: Guide) {
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { PaperPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("ПАМЯТКА · ЛАТВИЯ",fontSize=10.sp,color=Moss);SectionTitle(guide.title) };IconButton(onClick={vm.bookmark(guide.id)}) {Icon(if(guide.id in vm.bookmarks)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,"Сохранить памятку",tint=Moss)} }
            Text(guide.summary)
            Text("Учебная карточка. Медицинские назначения не выполняются.",fontSize=11.sp,color=Rust)
        } }
        itemsIndexed(guide.steps) { index,step -> PaperPanel(Modifier.fillMaxWidth()) { Text("${index+1} / ${guide.steps.size}",fontSize=12.sp,color=Moss,fontWeight=FontWeight.Bold);Text(step,fontSize=17.sp,lineHeight=25.sp) } }
        item { PaperPanel(Modifier.fillMaxWidth()) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.WarningAmber,null,tint=Rust);Spacer(Modifier.width(8.dp));Text("Важно",fontFamily=Heading,fontSize=27.sp,color=Rust) };guide.warnings.forEach { Text("• $it",fontSize=14.sp,lineHeight=21.sp) } } }
        item { Action("Слушать памятку",Icons.Outlined.VolumeUp,Modifier.fillMaxWidth()) {vm.voice.speak((guide.steps+guide.warnings).joinToString(" "))} }
        item { Action("Разобрать со спутником",Icons.Outlined.ChatBubbleOutline,Modifier.fillMaxWidth()) {vm.ask(guide.title)} }
        if(vm.modelReady&&!guide.critical)item { OutlinedButton(onClick={vm.ask("Объясни подробнее: ${guide.title}")},modifier=Modifier.fillMaxWidth()) {Text("Пояснение локального AI")} }
        item { PaperPanel(Modifier.fillMaxWidth()) {
            Text("Источники",fontFamily=Heading,fontSize=27.sp)
            Text("Краткое изложение хранится в приложении. Оригиналы открываются в браузере и могут требовать сеть. Отдельная профессиональная проверка карточек ещё не проведена.",fontSize=11.sp,lineHeight=16.sp)
            guide.sources.forEach { source->TextButton(onClick={openUrl(context,source.url)}) {Text(source.title,fontSize=13.sp)} }
        } }
    }
}

@Composable private fun PackScreen(vm: PutnikModel) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PaperPanel(Modifier.fillMaxWidth()) {
            SectionTitle("Перед выходом")
            Text("Собрано ${vm.checked.count {it.value}} из ${vm.checked.size}",fontSize=12.sp,color=Moss)
            vm.checked.keys.toList().forEach { item-> Row(Modifier.fillMaxWidth().clickable{vm.checkGear(item,vm.checked[item]!=true)}.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {Checkbox(vm.checked[item]==true,{vm.checkGear(item,it)},modifier=Modifier.testTag("gear-$item"),colors=CheckboxDefaults.colors(checkedColor=Moss));Text(item,Modifier.weight(1f),fontSize=16.sp)} }
        } }
        item { SectionTitle("Готовность без сети") }
        item { PaperPanel(Modifier.fillMaxWidth()) { Readiness("Русские памятки","10 карточек в приложении",true);Readiness("Чтение вслух",vm.voiceStatus,vm.voice.canSpeak);Text("Пакеты скачиваются только по твоему нажатию.",fontSize=11.sp) } }
        items(Packs.all,key={it.id}) { pack ->
            val installed=vm.installed(pack)
            PaperPanel(Modifier.fillMaxWidth()) {
                Readiness(pack.title,if(installed)"Установлен · офлайн" else "Не установлен · ${formatBytes(pack.bytes)}",installed)
                if(vm.activePack==pack.id) {
                    LinearProgressIndicator(progress={vm.downloadBytes.toFloat()/pack.bytes.toFloat()},modifier=Modifier.fillMaxWidth(),color=Moss)
                    Text("${formatBytes(vm.downloadBytes)} / ${formatBytes(pack.bytes)}",fontSize=12.sp)
                    TextButton(onClick=vm::cancelDownload) {Text("Остановить загрузку")}
                } else if(!installed)Action("Скачать ${formatBytes(pack.bytes)}",Icons.Outlined.Download,Modifier.fillMaxWidth(),enabled=vm.activePack==null) {vm.install(pack)}
                if(pack.id=="model"&&installed) {
                    Text(vm.modelStatus,fontSize=12.sp)
                    Action(if(vm.modelReady)"Выгрузить AI из памяти" else "Включить локальный AI",Icons.Outlined.AutoAwesome,Modifier.fillMaxWidth(),enabled=!vm.thinking) {if(vm.modelReady)vm.unloadModel()else vm.enableModel()}
                    Text("Без AI спутник ведёт по памяткам. С AI доступны дополнительные пояснения; их качество на твоём телефоне ещё предстоит оценить.",fontSize=11.sp,lineHeight=16.sp)
                }
            }
        }
        if(vm.packStatus.isNotBlank())item { Text(vm.packStatus,fontSize=13.sp) }
        item { PaperPanel(Modifier.fillMaxWidth()) {SectionTitle("Полевые заметки");OutlinedTextField(vm.note,vm::saveNote,Modifier.fillMaxWidth(),placeholder={Text("Маршрут, вещи, ориентиры…")},minLines=3,maxLines=8)} }
        item { Action("Настройки голоса и характера",Icons.Outlined.Tune,Modifier.fillMaxWidth()) {vm.page=Page.SETTINGS} }
    }
}

@Composable private fun Readiness(title: String,detail: String,ready: Boolean) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(if(ready)Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,tint=if(ready)Moss else Rust,modifier=Modifier.size(25.dp))
        Column(Modifier.weight(1f)) { Text(title,fontFamily=Body,fontWeight=FontWeight.Medium,fontSize=16.sp);Text(detail,fontSize=11.sp,lineHeight=16.sp,color=Ink.copy(alpha=.75f)) }
    }
}

@Composable private fun HelpScreen(vm: PutnikModel) {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p -> if(p.values.any {it})vm.startPosition() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PaperPanel(Modifier.fillMaxWidth()) {
            SectionTitle("Спокойно. По порядку.")
            Text("При угрозе жизни — экстренная служба 112. Для вызова необходимо доступное покрытие сети. Приложение не отправляет SOS автоматически.",fontSize=16.sp,lineHeight=24.sp)
            Button(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:112")))}.onFailure{Toast.makeText(context,"Не удалось открыть системный вызов",Toast.LENGTH_LONG).show()}},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),shape=RoundedCornerShape(10.dp),colors=ButtonDefaults.buttonColors(containerColor=Rust,contentColor=Ivory)) { Icon(Icons.Outlined.Call,null);Spacer(Modifier.width(10.dp));Text("Открыть вызов 112",fontSize=17.sp) }
        } }
        item { PaperPanel(Modifier.fillMaxWidth()) {
            Text("Координаты",fontFamily=Heading,fontSize=29.sp)
            val point=vm.location
            if(point==null)Text("Актуальная точка ещё не получена.") else {
                Text(String.format(Locale.ROOT,"%.6f, %.6f",point.latitude,point.longitude),fontWeight=FontWeight.Bold,fontSize=21.sp)
                Text("Точность ±${point.accuracy.toInt()} м · ${SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(point.time)}",fontSize=12.sp)
                if(System.currentTimeMillis()-point.time>120000)Text("Точка устарела. Получи новое положение.",color=Rust)
            }
            Text(vm.locationStatus,fontSize=12.sp)
            Action("Получить точку GPS",Icons.Outlined.MyLocation,Modifier.fillMaxWidth()) {permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
            if(point!=null)OutlinedButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"Мои координаты: ${point.latitude}, ${point.longitude}. Точность ±${point.accuracy.toInt()} м. Время точки: ${java.util.Date(point.time)}")},"Передать координаты"))},modifier=Modifier.fillMaxWidth()) {Text("Поделиться координатами")}
        } }
        item { Action("Первая помощь",Icons.Outlined.MedicalServices,Modifier.fillMaxWidth()) { vm.openGuide(GuideLibrary.byId("first-aid")!!) } }
        item { Action("Я потерялся",Icons.Outlined.Explore,Modifier.fillMaxWidth()) { vm.openGuide(GuideLibrary.byId("lost")!!) } }
        item { Text("Текущий экран не даёт диагнозов и не заменяет указания диспетчера.",fontSize=12.sp) }
    }
}

@Composable private fun SettingsScreen(vm: PutnikModel) {
    val context=LocalContext.current
    var confirmClear by remember {mutableStateOf(false)}
    if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Очистить диалог?")},text={Text("Сообщения будут удалены с этого устройства. Заметки, рюкзак и трек сохранятся.")},confirmButton={TextButton(onClick={vm.clearChat();confirmClear=false}){Text("Очистить")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Отмена")}})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PaperPanel(Modifier.fillMaxWidth()) {
            SectionTitle("Характер спутника")
            Personality.entries.forEach { value->Row(Modifier.fillMaxWidth().clickable{vm.setTone(value)}.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(vm.personality==value,{vm.setTone(value)});Text(value.label)} }
            Text("Pohuy Ultra задаёт манеру локального AI. Медицинские и экстренные карточки всегда нейтральны. Без AI ответы остаются сценарными.",fontSize=12.sp,lineHeight=18.sp)
        } }
        item { PaperPanel(Modifier.fillMaxWidth()) {
            SectionTitle("Голос")
            Text(vm.voiceStatus,fontSize=14.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {Text("Читать ответы вслух",Modifier.weight(1f));Switch(vm.autoRead,vm::setRead)}
            Action("Проверить русский голос",Icons.Outlined.VolumeUp,Modifier.fillMaxWidth()) {vm.voice.speak("Я Путник. Памятки доступны без интернета. Проверь карту и снаряжение перед выходом.")}
            OutlinedButton(onClick={runCatching {context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))}.onFailure {Toast.makeText(context,"Открой настройки синтеза речи Android и установи русский офлайн-голос",Toast.LENGTH_LONG).show()}},modifier=Modifier.fillMaxWidth()) {Text("Установить голос Android")}
            Text("Запись запускается только кнопкой. Аудиофайлы не сохраняются и не отправляются в облако.",fontSize=11.sp,lineHeight=16.sp)
        } }
        item { PaperPanel(Modifier.fillMaxWidth()) {Row(verticalAlignment=Alignment.CenterVertically) {SectionTitle("Полевой светлый режим",Modifier.weight(1f));Switch(vm.daylight,{vm.toggleDay()})}} }
        item { PaperPanel(Modifier.fillMaxWidth()) {
            SectionTitle("Приватность")
            Text("Без аккаунта и аналитики. История, заметки, снаряжение и трек хранятся на устройстве. Сеть используется при нажатии на загрузку или внешний источник.",fontSize=14.sp,lineHeight=21.sp)
            TextButton(onClick={confirmClear=true}) {Text("Очистить историю диалога")}
        } }
        item { PaperPanel(Modifier.fillMaxWidth()) {
            SectionTitle("Путник · 0.1 alpha")
            Text("Русский survival kit для Латвии. Первая рабочая альфа, не сертифицированное средство спасения. Полевые испытания и профессиональная редактура ещё не завершены.",fontSize=12.sp,lineHeight=18.sp)
            TextButton(onClick={openUrl(context,"https://github.com/Kurubik/survival-kit")}) {Text("Код, лицензии и ограничения")}
        } }
    }
}

private fun openUrl(context: android.content.Context,url: String) { runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure {Toast.makeText(context,"Не удалось открыть браузер",Toast.LENGTH_SHORT).show()} }
fun formatBytes(bytes: Long): String = if(bytes>=1_000_000_000L)String.format(Locale.ROOT,"%.2f ГБ",bytes/1_000_000_000.0) else String.format(Locale.ROOT,"%.1f МБ",bytes/1_000_000.0)
