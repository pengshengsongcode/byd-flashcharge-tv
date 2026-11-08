package io.github.pengshengsongcode.bydflashchargetv;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 原生电视导航与站点详情；地图仅加载高德底图，不套用网页界面。 */
public class MainActivity extends Activity {
    private static final String API = "https://byd-flashcharge-pages-api.netlify.app";
    private static final String MAP_URL = "https://pengshengsongcode.github.io/byd-flashcharge-pages/tv-map.html";
    private static final int BG = Color.rgb(11, 23, 24), PANEL = Color.rgb(21, 39, 38), TEXT = Color.rgb(239, 246, 240), MUTED = Color.rgb(169, 192, 180), ACCENT = Color.rgb(107, 222, 167);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final ExecutorService detailIo = Executors.newSingleThreadExecutor();
    private final List<Station> stations = new ArrayList<>();
    private final Map<String,String> names = new HashMap<>();
    private final Map<String,JSONObject> details = new HashMap<>();
    private final Map<String,JSONObject> recent = new HashMap<>();
    private JSONObject districts = new JSONObject();
    private String province = "", city = "", district = "", selectedCode = "";
    private String sourceTime = "", sourceState = "加载中";
    private boolean mapReady = false, panMode = false, recentOnly = false, recentAvailable = false;
    private volatile int requestId = 0;
    private Runnable detailPending;
    private Runnable mapPending;
    private Runnable stationMapPending;
    private volatile int mapGeneration = 0;
    private volatile long nextDetailAt = 0;
    private WebView map;
    private LinearLayout list, mapBar;
    private ScrollView listScroll;
    private TextView headline, subhead, status, detailName, detailAddress, detailHours, detailHint, moveButton, zoomOut;

    static final class Station {
        String code, province, city, district; double lng, lat; int state;
        Station(JSONObject o) {
            code=o.optString("stationCode"); province=o.optString("provinceCode"); city=o.optString("cityCode"); state=o.isNull("status")?-1:o.optInt("status",-1);
            JSONArray point=o.optJSONArray("poi"); lng=point==null?0:point.optDouble(0,0); lat=point==null?0:point.optDouble(1,0);
            district="行政区待确认";
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        buildUi();
        io.execute(() -> {
            try {
                JSONObject idx = readAsset("data/district-index.json").getJSONObject("stations");
                JSONObject snapshot = readAsset("data/snapshot.json");
                JSONObject beijing = readAsset("data/beijing-snapshot.json").optJSONObject("entries");
                ui.post(() -> { districts=idx; if(beijing!=null) for(java.util.Iterator<String> it=beijing.keys();it.hasNext();) { String key=it.next(); JSONObject item=beijing.optJSONObject(key); if(item!=null && item.optJSONObject("data")!=null) details.put(key,item.optJSONObject("data")); } applyData(snapshot, false); refresh(); });
            } catch (Exception e) { ui.post(() -> { sourceState="本地数据不可用"; updateStatus(); refresh(); }); }
        });
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(9));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    private StateListDrawable focusBackground(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},shape(Color.rgb(28,91,69),ACCENT));d.addState(new int[]{android.R.attr.state_pressed},shape(Color.rgb(34,105,75),ACCENT));d.addState(new int[]{},shape(Color.rgb(27,48,46),0));return d;}
    private TextView control(String label){TextView b=text(label,16,TEXT,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(14),0,dp(14),0);b.setFocusable(true);b.setClickable(true);b.setBackground(focusBackground());return b;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(28),dp(20),dp(28),dp(14));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("ϟ  比亚迪闪充",23,TEXT,true);header.addView(logo,lp(205,48));
        TextView all=control("全国站点"), bj=control("北京站点"), recentButton=control("最近新增"), reload=control("刷新数据");
        header.addView(all,lp(116,44));LinearLayout.LayoutParams h=lp(116,44);h.leftMargin=dp(10);header.addView(bj,h);
        h=lp(116,44);h.leftMargin=dp(10);header.addView(recentButton,h);h=lp(116,44);h.leftMargin=dp(10);header.addView(reload,h);
        status=text("正在准备站点数据…",13,MUTED,false);status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);header.addView(status,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(header,lp(-1,52));
        LinearLayout content=new LinearLayout(this);LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,0,1);body.topMargin=dp(14);root.addView(content,body);
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(dp(14),dp(12),dp(14),dp(8));left.setBackground(shape(PANEL,0));content.addView(left,lp(334,-1));
        headline=text("全国站点",20,TEXT,true);subhead=text("选择省份开始浏览",13,MUTED,false);left.addView(headline,lp(-1,30));left.addView(subhead,lp(-1,26));
        listScroll=new ScrollView(this);listScroll.setFillViewport(false);listScroll.setFocusable(false);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);listScroll.addView(list);left.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout frame=new FrameLayout(this);frame.setBackground(shape(Color.rgb(223,232,223),0));LinearLayout.LayoutParams mapParams=new LinearLayout.LayoutParams(0,-1,1);mapParams.leftMargin=dp(16);content.addView(frame,mapParams);
        map=new WebView(this);map.setBackgroundColor(Color.rgb(219,229,220));map.setFocusable(false);map.setFocusableInTouchMode(false);map.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        WebSettings ws=map.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        map.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){if(MAP_URL.equals(req.getUrl().toString()))try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("map.html"));}catch(Exception ignored){}return null;}
            @Override public void onPageFinished(WebView view,String url){if(url.equals(MAP_URL)){mapReady=true;updateMap();}}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return req.isForMainFrame()&&!MAP_URL.equals(req.getUrl().toString());}
        });frame.addView(map,new FrameLayout.LayoutParams(-1,-1));
        mapBar=new LinearLayout(this);mapBar.setGravity(Gravity.CENTER_VERTICAL);FrameLayout.LayoutParams controls=new FrameLayout.LayoutParams(-2,dp(42),Gravity.TOP|Gravity.RIGHT);controls.setMargins(0,dp(12),dp(12),0);frame.addView(mapBar,controls);
        zoomOut=control("−");zoomOut.setId(View.generateViewId());TextView zoomIn=control("＋"),reset=control("复位");moveButton=control("移动地图");
        for(TextView b:new TextView[]{zoomOut,zoomIn,reset,moveButton}){LinearLayout.LayoutParams p=lp(-2,40);p.leftMargin=dp(6);mapBar.addView(b,p);}
        zoomOut.setOnClickListener(v->js("tvZoom(-1)"));zoomIn.setOnClickListener(v->js("tvZoom(1)"));reset.setOnClickListener(v->js("tvReset()"));moveButton.setOnClickListener(v->setPanMode(!panMode));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(12),dp(18),dp(12));card.setBackground(shape(Color.rgb(15,35,32),0));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);cp.setMargins(dp(12),0,dp(12),dp(32));frame.addView(card,cp);
        detailName=text("选择左侧区域或站点",18,TEXT,true);detailName.setMaxLines(2);detailAddress=text("地图将随选择自动定位",13,MUTED,false);detailAddress.setMaxLines(2);detailHours=text("",13,ACCENT,false);detailHint=text("方向键选择 · 确定进入 · 返回上一级",12,MUTED,false);card.addView(detailName,lp(-1,-2));card.addView(detailAddress,lp(-1,-2));card.addView(detailHours,lp(-1,-2));card.addView(detailHint,lp(-1,-2));
        TextView footer=text("方向键移动焦点   ·   确定键选择   ·   返回键上一级",12,MUTED,false);root.addView(footer,lp(-1,28));setContentView(root);
        all.setOnClickListener(v->{recentOnly=false;recentButton.setText("最近新增");province="";city="";district="";clearSelection();renderList(true);});
        bj.setOnClickListener(v->{province="110000";city=beijingCity();district="";clearSelection();renderList(true);});
        recentButton.setOnClickListener(v->{if(!recentAvailable&&!recentOnly){Toast.makeText(this,"官方近期新站清单暂不可用",Toast.LENGTH_LONG).show();return;}recentOnly=!recentOnly;recentButton.setText(recentOnly?"新增：开":"最近新增");clearSelection();renderList(true);});
        reload.setOnClickListener(v->refresh());
        map.loadUrl(MAP_URL);all.requestFocus();
    }
    private void setPanMode(boolean value){panMode=value;moveButton.setText(value?"完成移动":"移动地图");detailHint.setText(value?"方向键移动地图 · 确定或返回退出":"方向键选择 · 确定进入 · 返回上一级");}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(panMode&&e.getAction()==KeyEvent.ACTION_DOWN){int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_DPAD_LEFT){js("tvPan(100,0)");return true;}if(k==KeyEvent.KEYCODE_DPAD_RIGHT){js("tvPan(-100,0)");return true;}if(k==KeyEvent.KEYCODE_DPAD_UP){js("tvPan(0,100)");return true;}if(k==KeyEvent.KEYCODE_DPAD_DOWN){js("tvPan(0,-100)");return true;}if(k==KeyEvent.KEYCODE_BACK||k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){setPanMode(false);return true;}}return super.dispatchKeyEvent(e);}
    private void js(String script){if(mapReady)map.evaluateJavascript("if(window."+script.substring(0,script.indexOf('('))+"){"+script+"}",null);}
    private JSONObject readAsset(String path)throws Exception{try(InputStream in=getAssets().open(path)){return new JSONObject(read(in));}}
    private String read(InputStream input)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1)out.write(bytes,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    private JSONObject fetch(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");try{int code=c.getResponseCode();if(code==429){long seconds=60;try{seconds=Math.max(1,Long.parseLong(c.getHeaderField("Retry-After")));}catch(Exception ignored){}nextDetailAt=System.currentTimeMillis()+seconds*1000;throw new Exception("官方限流中，请稍后重试");}if(code!=200)throw new Exception("接口暂不可用（"+code+"）");try(InputStream in=c.getInputStream()){return new JSONObject(read(in));}}finally{c.disconnect();}}
    private void refresh(){sourceState="正在更新";updateStatus();io.execute(()->{try{JSONObject data=fetch(API+"/api/stations");ui.post(()->applyData(data,true));}catch(Exception e){ui.post(()->{sourceState="更新失败 · 使用已有数据";updateStatus();});}});}
    private void applyData(JSONObject data,boolean live){
        JSONArray all=data.optJSONArray("stations"), regions=data.optJSONArray("provinces");if(all==null||regions==null)return;
        stations.clear();names.clear();recent.clear();
        for(int i=0;i<regions.length();i++){JSONObject r=regions.optJSONObject(i);if(r!=null)names.put(r.optString("code"),r.optString("name"));}
        JSONArray fresh=data.optJSONArray("recent");if(fresh!=null)for(int i=0;i<fresh.length();i++){JSONObject r=fresh.optJSONObject(i);if(r!=null)recent.put(r.optString("stationCode"),r);}
        for(int i=0;i<all.length();i++){JSONObject row=all.optJSONObject(i);if(row==null)continue;Station s=new Station(row);if(s.code.isEmpty()||s.lng==0||s.lat==0)continue;JSONArray idx=districts.optJSONArray(s.code);if(idx!=null&&idx.optDouble(0)==s.lng&&idx.optDouble(1)==s.lat&&s.province.equals(idx.optString(3))&&s.city.equals(idx.optString(4)))s.district=idx.optString(2,"行政区待确认");stations.add(s);}
        JSONObject meta=data.optJSONObject("meta");sourceTime=meta==null?"":meta.optString("updateTime");recentAvailable=meta!=null&&meta.optBoolean("recentAvailable",false);sourceState=live&&meta!=null&&!meta.optBoolean("stale")?"实时数据":"离线快照";updateStatus();renderList(false);
    }
    private void updateStatus(){String t=sourceTime.matches("\\d{8}-\\d{6}")?sourceTime.substring(0,4)+"-"+sourceTime.substring(4,6)+"-"+sourceTime.substring(6,8)+" "+sourceTime.substring(9,11)+":"+sourceTime.substring(11,13):"时间待确认";status.setText(sourceState+"  ·  "+t);}
    private String beijingCity(){for(Station s:stations)if(s.province.equals("110000"))return s.city;return "110100";}
    private boolean isRecent(Station s){return recent.containsKey(s.code);}
    private List<Station> visibleStations(){List<Station> out=new ArrayList<>();for(Station s:stations)if((!recentOnly||isRecent(s))&&(province.isEmpty()||s.province.equals(province))&&(city.isEmpty()||s.city.equals(city))&&(district.isEmpty()||s.district.equals(district)))out.add(s);return out;}
    private void clearSelection(){selectedCode="";requestId++;if(detailPending!=null)ui.removeCallbacks(detailPending);if(stationMapPending!=null)ui.removeCallbacks(stationMapPending);js("tvClearSelection()");detailName.setText("选择左侧区域或站点");detailAddress.setText("地图将随选择自动定位");detailHours.setText("");}
    private interface Choice{void select(String code);}
    private void addCounts(Map<String,Integer> counts,String code){counts.put(code,counts.containsKey(code)?counts.get(code)+1:1);}
    private List<Map.Entry<String,Integer>> ordered(Map<String,Integer> counts){List<Map.Entry<String,Integer>> rows=new ArrayList<>(counts.entrySet());Collections.sort(rows,(a,b)->b.getValue()-a.getValue());return rows;}
    private void addRegion(String name,String count,Runnable click){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(3),dp(12),dp(3));row.setFocusable(true);row.setClickable(true);row.setBackground(focusBackground());
        configureRow(row);TextView label=text(name,16,TEXT,true), right=text(count+"   ›",14,MUTED,false);right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(label,new LinearLayout.LayoutParams(0,-1,1));row.addView(right,lp(70,-1));LinearLayout.LayoutParams p=lp(-1,49);p.bottomMargin=dp(5);list.addView(row,p);row.setOnFocusChangeListener((v,f)->{if(f)zoomOut.setNextFocusLeftId(row.getId());});row.setOnClickListener(v->click.run());
    }
    private void configureRow(LinearLayout row){row.setId(View.generateViewId());row.setNextFocusRightId(zoomOut.getId());}
    private void addStation(Station s,int ordinal){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(5),dp(12),dp(5));row.setFocusable(true);row.setClickable(true);row.setBackground(focusBackground());
        JSONObject info=details.containsKey(s.code)?details.get(s.code):recent.get(s.code);String name=info==null?"站点 "+ordinal+" · 选择查看详情":info.optString("stationName","站点详情待获取");
        TextView label=text(name,15,TEXT,true);label.setMaxLines(2);TextView area=text(s.district+(s.state==0?" · 地图未展示":""),12,MUTED,false);row.addView(label,lp(-1,-2));row.addView(area,lp(-1,-2));LinearLayout.LayoutParams p=lp(-1,62);p.bottomMargin=dp(5);list.addView(row,p);
        configureRow(row);row.setOnFocusChangeListener((v,focus)->{if(focus){zoomOut.setNextFocusLeftId(row.getId());selectStation(s,label,false);}});row.setOnClickListener(v->selectStation(s,label,true));
    }
    /** 大城市的站点分批创建，避免切换城市时一次构造数百个电视控件。 */
    private void addStationsPage(List<Station> rows,int start){
        int end=Math.min(start+20,rows.size());
        for(int i=start;i<end;i++)addStation(rows.get(i),i+1);
        if(end<rows.size()){
            LinearLayout more=new LinearLayout(this);more.setGravity(Gravity.CENTER_VERTICAL);more.setPadding(dp(12),dp(3),dp(12),dp(3));more.setFocusable(true);more.setClickable(true);more.setBackground(focusBackground());configureRow(more);
            TextView label=text("加载更多站点  "+end+" / "+rows.size(),15,ACCENT,true);more.addView(label,new LinearLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams p=lp(-1,49);p.bottomMargin=dp(5);list.addView(more,p);
            more.setOnFocusChangeListener((v,f)->{if(f)zoomOut.setNextFocusLeftId(more.getId());});
            more.setOnClickListener(v->{int firstNew=list.indexOfChild(more);list.removeView(more);addStationsPage(rows,end);if(firstNew<list.getChildCount())list.getChildAt(firstNew).requestFocus();});
        }
    }
    private void renderList(boolean focus){
        View previous=list.findFocus();int previousIndex=previous==null?-1:list.indexOfChild(previous);list.removeAllViews();if(focus)listScroll.scrollTo(0,0);if(stations.isEmpty()){headline.setText("站点数据加载中");subhead.setText("请稍候");return;}
        List<Station> filtered=visibleStations();String pName=names.containsKey(province)?names.get(province):province,cName=names.containsKey(city)?names.get(city):((province.equals("110000")||province.equals("120000")||province.equals("310000")||province.equals("500000"))?pName:city);
        if(province.isEmpty()){
            headline.setText(recentOnly?"全国 · 最近新增":"全国站点");subhead.setText(filtered.size()+" 条站点记录 · 选择省份");Map<String,Integer> counts=new LinkedHashMap<>();for(Station s:filtered)addCounts(counts,s.province);
            for(Map.Entry<String,Integer> row:ordered(counts)){String code=row.getKey();addRegion(names.containsKey(code)?names.get(code):code,String.valueOf(row.getValue()),()->{province=code;city="";district="";clearSelection();renderList(true);});}
        }else if(city.isEmpty()){
            headline.setText(pName);subhead.setText(filtered.size()+" 条站点记录 · 选择城市");Map<String,Integer> counts=new LinkedHashMap<>();for(Station s:filtered)addCounts(counts,s.city);
            for(Map.Entry<String,Integer> row:ordered(counts)){String code=row.getKey();addRegion(names.containsKey(code)?names.get(code):code,String.valueOf(row.getValue()),()->{city=code;district="";clearSelection();renderList(true);});}
        }else if(district.isEmpty()){
            headline.setText(cName);subhead.setText(filtered.size()+" 条站点记录 · 选择区县或站点");Map<String,Integer> counts=new LinkedHashMap<>();for(Station s:filtered)addCounts(counts,s.district);
            for(Map.Entry<String,Integer> row:ordered(counts)){String code=row.getKey();addRegion(code,String.valueOf(row.getValue()),()->{district=code;clearSelection();renderList(true);});}
            TextView title=text("全部站点",14,ACCENT,true);list.addView(title,lp(-1,34));addStationsPage(filtered,0);
        }else{
            headline.setText(district);subhead.setText(pName+" · "+cName+"   "+filtered.size()+" 条站点");addStationsPage(filtered,0);
        }
        if(recentOnly)subhead.setText(subhead.getText()+" · 官方近期清单");if(filtered.isEmpty()){TextView empty=text(recentOnly?"当前范围在官方清单中无站点":"当前范围没有站点",15,MUTED,false);list.addView(empty,lp(-1,60));}updateMap();if((focus||previousIndex>=0)&&list.getChildCount()>0){View first=list.getChildAt(focus?0:Math.min(previousIndex,list.getChildCount()-1));if(!first.isFocusable()&&list.getChildCount()>1)first=list.getChildAt(1);first.requestFocus();}
    }
    /** 合并快速的省市切换，并在后台序列化坐标，防止阻塞遥控器响应。 */
    private void updateMap(){
        int generation=++mapGeneration;
        if(mapPending!=null)ui.removeCallbacks(mapPending);
        if(!mapReady)return;
        List<Station> snapshot=visibleStations();boolean scoped=!province.isEmpty();
        mapPending=()->io.execute(()->{
            JSONArray pts=new JSONArray();for(Station s:snapshot)if(s.state!=0){JSONArray p=new JSONArray();try{p.put(s.lng);p.put(s.lat);pts.put(p);}catch(Exception ignored){}}
            JSONObject payload=new JSONObject();try{payload.put("points",pts);payload.put("scope",scoped);}catch(Exception ignored){}
            String script="window.tvSetPoints&&window.tvSetPoints("+payload+")";
            ui.post(()->{if(generation==mapGeneration&&mapReady)map.evaluateJavascript(script,null);});
        });
        ui.postDelayed(mapPending,220);
    }
    private void selectStation(Station s,TextView label,boolean force){
        selectedCode=s.code;int id=++requestId;if(detailPending!=null)ui.removeCallbacks(detailPending);
        if(stationMapPending!=null)ui.removeCallbacks(stationMapPending);
        if(s.state!=0){stationMapPending=()->{if(id==requestId)js("tvSelect("+s.lng+","+s.lat+")");};if(force)stationMapPending.run();else ui.postDelayed(stationMapPending,220);}
        JSONObject known=details.containsKey(s.code)?details.get(s.code):recent.get(s.code);
        showDetail(s,known,known==null?"按确定获取官方详情":"");
        long checked=known==null?0:known.optLong("checkedAt",0);if(known!=null&&!force&&checked>0&&System.currentTimeMillis()-checked<300000)return;
        long delay=Math.max(500,nextDetailAt-System.currentTimeMillis());
        detailPending=()->{if(id!=requestId)return;detailIo.execute(()->{try{long wait=nextDetailAt-System.currentTimeMillis();if(wait>0)Thread.sleep(wait);if(id!=requestId)return;nextDetailAt=System.currentTimeMillis()+1500;JSONObject info=fetch(API+"/api/station?id="+Uri.encode(s.code));ui.post(()->{details.put(s.code,info);label.setText(info.optString("stationName","站点详情"));if(id==requestId)showDetail(s,info,"");});}catch(Exception e){ui.post(()->{if(id==requestId)showDetail(s,known,System.currentTimeMillis()<nextDetailAt-1500?"官方限流中，请稍后重试":"详情暂不可用，请按确定重试");});}});};ui.postDelayed(detailPending,delay);
    }
    private void showDetail(Station s,JSONObject data,String hint){detailName.setText(data==null?"站点详情":data.optString("stationName","站点详情"));detailAddress.setText(data==null?s.district:data.optString("address",s.district));String hours=data==null?"":data.optString("businessHours","");long checked=data==null?0:data.optLong("checkedAt",0);String time="";if(checked>0){java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA);fmt.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));time="  ·  核对 "+fmt.format(new java.util.Date(checked));}detailHours.setText(hours.isEmpty()?hint:"营业时间  "+hours+time+(hint.isEmpty()?"":"  ·  "+hint));}
    @Override public void onBackPressed(){if(panMode){setPanMode(false);return;}if(!district.isEmpty())district="";else if(!city.isEmpty())city="";else if(!province.isEmpty())province="";else{super.onBackPressed();return;}clearSelection();renderList(true);}
    @Override protected void onDestroy(){requestId++;mapGeneration++;if(detailPending!=null)ui.removeCallbacks(detailPending);if(mapPending!=null)ui.removeCallbacks(mapPending);if(stationMapPending!=null)ui.removeCallbacks(stationMapPending);io.shutdownNow();detailIo.shutdownNow();if(map!=null){map.stopLoading();map.destroy();}super.onDestroy();}
}
