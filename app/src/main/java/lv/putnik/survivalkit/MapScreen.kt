package lv.putnik.survivalkit

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes

@Composable fun MapScreen(vm: PutnikModel) {
    val context=LocalContext.current
    var centerRequest by remember { mutableIntStateOf(0) }
    var lastCenter by remember { mutableIntStateOf(-1) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val overlays=remember { mutableListOf<Layer>() }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p->if(p.values.any {it})vm.startPosition() }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri->
        if(uri!=null)context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Putnik\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Путник</name><trkseg>")
            vm.track.forEach {p->out.write("<trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\"><time>${java.time.Instant.ofEpochMilli(p.time)}</time></trkpt>")}
            out.write("</trkseg></trk></gpx>")
        }
    }
    DisposableEffect(Unit) {onDispose {mapView?.destroyAll();mapView=null}}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(vm.installed(Packs.map)) {
            AndroidView(factory={ ctx->
                AndroidGraphicFactory.createInstance(context.applicationContext as android.app.Application)
                MapView(ctx).apply {
                    isClickable=true;setBuiltInZoomControls(true);mapScaleBar.isVisible=true
                    model.displayModel.fixedTileSize=256
                    val cache=AndroidUtil.createTileCache(ctx,"latvia-cache",model.displayModel.tileSize,1f,model.frameBufferModel.overdrawFactor.toDouble())
                    val data=MapFile(Packs.file(ctx,Packs.map))
                    val renderer=TileRendererLayer(cache,data,model.mapViewPosition,AndroidGraphicFactory.INSTANCE)
                    renderer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
                    layerManager.layers.add(renderer)
                    setCenter(LatLong(vm.camp?.latitude?:56.9496,vm.camp?.longitude?:24.1052));setZoomLevel(11)
                    mapView=this
                }
            },modifier=Modifier.weight(1f).fillMaxWidth(),update={ map->
                overlays.forEach {map.layerManager.layers.remove(it);it.onDestroy()};overlays.clear()
                if(vm.track.size>1) {
                    val paint=AndroidGraphicFactory.INSTANCE.createPaint().apply { color=0xFF883D2C.toInt();strokeWidth=6f;style=org.mapsforge.core.graphics.Style.STROKE }
                    val line=Polyline(paint,AndroidGraphicFactory.INSTANCE)
                    line.latLongs.addAll(vm.track.map {LatLong(it.latitude,it.longitude)})
                    map.layerManager.layers.add(line);overlays.add(line)
                }
                fun point(lat: Double,lon: Double,color: Int) {
                    val bitmap=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.color=0xFFE9DFC9.toInt();canvas.drawCircle(20f,20f,18f,paint)
                    paint.color=color;canvas.drawCircle(20f,20f,13f,paint)
                    val icon=AndroidGraphicFactory.convertToBitmap(BitmapDrawable(context.resources,bitmap))
                    val marker=Marker(LatLong(lat,lon),icon,0,0)
                    map.layerManager.layers.add(marker);overlays.add(marker)
                }
                vm.camp?.let {point(it.latitude,it.longitude,0xFF65754F.toInt())}
                vm.location?.let {p->
                    point(p.latitude,p.longitude,0xFF3564A2.toInt())
                    if(lastCenter!=centerRequest){map.setCenter(LatLong(p.latitude,p.longitude));lastCenter=centerRequest}
                }
                map.layerManager.redrawLayers()
            })
            Text("© OpenStreetMap contributors · Mapsforge · данные карты не показывают текущие запреты и перекрытия",fontSize=10.sp,lineHeight=13.sp)
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.Center) {
                Text("Карта Латвии",style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Загрузи пакет ${formatBytes(Packs.map.bytes)} в рюкзаке. После установки карта, тропы и подписи работают без сети. Фоновые онлайн-тайлы не используются.")
                Spacer(Modifier.height(16.dp))
                Button(onClick={vm.page=Page.PACK}) {Text("Открыть загрузки")}
            }
        }
        Text(vm.locationStatus,fontSize=12.sp)
        vm.location?.let {p->Text(String.format(java.util.Locale.ROOT,"%.6f, %.6f",p.latitude,p.longitude),fontSize=13.sp)}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={centerRequest++;permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Моя точка")}
            OutlinedButton(onClick=vm::saveCamp,enabled=vm.location!=null,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Сохранить лагерь")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={if(vm.tracking)vm.stopPosition()else vm.startPosition(true)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(if(vm.tracking)"Остановить трек"else"Записать трек")}
            OutlinedButton(onClick={export.launch("putnik-track.gpx")},enabled=vm.track.isNotEmpty(),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Экспорт GPX")}
        }
        Text("Трек записывается только пока приложение открыто. Линия на карте не гарантирует безопасный путь.",fontSize=10.sp,lineHeight=14.sp)
    }
}
