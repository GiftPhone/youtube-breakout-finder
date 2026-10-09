package com.profilekeeper.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

/** Local-only Android browser with recoverable tab state. */
public class MainActivity extends Activity {
    static final int NAVY = Color.rgb(18,31,55), BLUE = Color.rgb(44,106,218);
    static final int PICK_FILE=71;
    static class Tab {
        String id=UUID.randomUUID().toString();
        String url="https://www.google.com/", title="New tab";
        boolean desktop=false;
        WebView web;
    }
    static class Profile {
        String id="p"+UUID.randomUUID().toString().replace("-","");
        String name="Profile";
        String selected="";
        ArrayList<Tab> tabs=new ArrayList<>();
        Profile() { Tab t=new Tab(); tabs.add(t); selected=t.id; }
    }
    ArrayList<Profile> profiles=new ArrayList<>();
    String selectedProfile="";
    boolean isolated=false, rebuilding=false, googleMessageShown=false;
    Spinner profileSelect;
    EditText address;
    LinearLayout tabStrip;
    FrameLayout browserFrame;
    Button desktopButton;
    TextView status;
    ValueCallback<Uri[]> uploadCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        isolated=WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        load();
        drawUi();
        updateProfileMenu();
        showTab();
        if(!isolated) new AlertDialog.Builder(this)
            .setTitle("Profile isolation unavailable")
            .setMessage("Your Android System WebView does not support separate profiles. Only one profile is enabled so sign-in cookies cannot leak between profiles. Updating System WebView may help.")
            .setPositiveButton("OK",null).show();
    }

    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    TextView label(String text,int size,int color){
        TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(size);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    Button btn(String text){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(12);
        b.setPadding(0,0,0,0);return b;
    }
    LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(0);r.setGravity(Gravity.CENTER_VERTICAL);return r;}

    void drawUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(-1);setContentView(root);
        LinearLayout bar=row();bar.setBackgroundColor(NAVY);bar.setPadding(dp(6),0,dp(6),0);
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView brand=label("KEEPER",14,-1);brand.setTypeface(null,1);
        bar.addView(brand,new LinearLayout.LayoutParams(dp(68),-1));
        profileSelect=new Spinner(this);
        bar.addView(profileSelect,new LinearLayout.LayoutParams(0,dp(48),1));
        Button plus=btn("+ Profile");bar.addView(plus,new LinearLayout.LayoutParams(dp(90),dp(42)));
        plus.setOnClickListener(v->createProfile());
        plus.setOnLongClickListener(v->{renameProfile();return true;});
        profileSelect.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> v){}
            @Override public void onItemSelected(android.widget.AdapterView<?> v,View child,int idx,long id){
                if(rebuilding||!isolated||idx>=profiles.size())return;
                Profile p=profiles.get(idx);if(p.id.equals(selectedProfile))return;
                saveEverything();selectedProfile=p.id;saveMetadata();showTab();
            }
        });
        LinearLayout nav=row();nav.setPadding(dp(3),0,dp(3),0);nav.setBackgroundColor(0xffedf2f9);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(52)));
        Button back=btn("‹");nav.addView(back,new LinearLayout.LayoutParams(dp(39),dp(45)));
        back.setOnClickListener(v->{WebView w=currentWeb();if(w!=null&&w.canGoBack())w.goBack();});
        Button forward=btn("›");nav.addView(forward,new LinearLayout.LayoutParams(dp(39),dp(45)));
        forward.setOnClickListener(v->{WebView w=currentWeb();if(w!=null&&w.canGoForward())w.goForward();});
        address=new EditText(this);address.setSingleLine(true);address.setTextSize(13);
        address.setSelectAllOnFocus(true);address.setHint("Search or URL");
        nav.addView(address,new LinearLayout.LayoutParams(0,dp(44),1));
        address.setOnEditorActionListener((v,a,e)->{navigate();return true;});
        Button go=btn("Go");nav.addView(go,new LinearLayout.LayoutParams(dp(43),dp(45)));
        go.setOnClickListener(v->navigate());
        Button reload=btn("↻");nav.addView(reload,new LinearLayout.LayoutParams(dp(40),dp(45)));
        reload.setOnClickListener(v->{WebView w=currentWeb();if(w!=null)w.reload();});
        browserFrame=new FrameLayout(this);
        root.addView(browserFrame,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);
        sc.setBackgroundColor(0xffe4ebf5);root.addView(sc,new LinearLayout.LayoutParams(-1,dp(52)));
        tabStrip=row();sc.addView(tabStrip);
        LinearLayout foot=row();foot.setPadding(dp(8),0,dp(8),0);foot.setBackgroundColor(NAVY);
        root.addView(foot,new LinearLayout.LayoutParams(-1,dp(44)));
        status=label("Tabs saved automatically",11,0xffdbe5f6);
        foot.addView(status,new LinearLayout.LayoutParams(0,-1,1));
        desktopButton=btn("Desktop off");foot.addView(desktopButton,new LinearLayout.LayoutParams(dp(96),dp(39)));
        desktopButton.setOnClickListener(v->toggleDesktop());
        Button chrome=btn("Chrome ↗");foot.addView(chrome,new LinearLayout.LayoutParams(dp(88),dp(39)));
        chrome.setOnClickListener(v->{Tab t=currentTab();if(t!=null)openChrome(t.url);});
    }

    Profile profile(){
        if(profiles.isEmpty()){Profile p=new Profile();p.name="Personal";profiles.add(p);selectedProfile=p.id;}
        for(Profile p:profiles)if(p.id.equals(selectedProfile))return p;
        selectedProfile=profiles.get(0).id;return profiles.get(0);
    }
    Tab currentTab(){
        Profile p=profile();for(Tab t:p.tabs)if(t.id.equals(p.selected))return t;
        if(p.tabs.isEmpty())p.tabs.add(new Tab());
        p.selected=p.tabs.get(0).id;return p.tabs.get(0);
    }
    WebView currentWeb(){Tab t=currentTab();return t==null?null:t.web;}
    boolean active(Tab t){return currentTab().id.equals(t.id);}

    void updateProfileMenu(){
        rebuilding=true;
        ArrayList<String> names=new ArrayList<>();
        int idx=0;for(int i=0;i<profiles.size();i++){
            names.add(profiles.get(i).name);if(profiles.get(i).id.equals(selectedProfile))idx=i;
        }
        if(!isolated && !names.isEmpty()){String first=names.get(0);names.clear();names.add(first);}
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,names){
            @Override public View getView(int pos,View old,android.view.ViewGroup parent){
                TextView t=label(getItem(pos),13,-1);t.setSingleLine(true);return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSelect.setAdapter(a);profileSelect.setSelection(isolated?idx:0);
        rebuilding=false;
    }
    void createProfile(){
        if(!isolated){Toast.makeText(this,"Separate profiles are not supported by WebView on this device",0).show();return;}
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setText("Profile "+(profiles.size()+1));
        new AlertDialog.Builder(this).setTitle("New browser profile").setView(name)
            .setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{
                saveEverything();Profile p=new Profile();
                String n=name.getText().toString().trim();p.name=n.isEmpty()?"Profile":n;
                profiles.add(p);selectedProfile=p.id;saveMetadata();updateProfileMenu();showTab();
            }).show();
    }
    void renameProfile(){
        Profile p=profile();EditText e=new EditText(this);e.setSingleLine(true);e.setText(p.name);
        new AlertDialog.Builder(this).setTitle("Rename profile").setView(e)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                String n=e.getText().toString().trim();if(!n.isEmpty())p.name=n;
                saveMetadata();updateProfileMenu();
            }).show();
    }
    void showTab(){
        Tab t=currentTab();
        browserFrame.removeAllViews();
        if(t.web==null)t.web=createWeb(t,profile());
        if(t.web.getParent() instanceof ViewGroup)((ViewGroup)t.web.getParent()).removeView(t.web);
        browserFrame.addView(t.web,new FrameLayout.LayoutParams(-1,-1));
        address.setText(t.url);
        desktopButton.setText(t.desktop?"Desktop on":"Desktop off");
        renderTabs();saveMetadata();
    }
    void renderTabs(){
        tabStrip.removeAllViews();Profile p=profile();
        for(Tab t:p.tabs){
            LinearLayout cell=row();
            cell.setBackgroundColor(t.id.equals(p.selected)?-1:0xffe4ebf5);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(145),dp(42));
            lp.setMargins(dp(2),0,dp(2),0);tabStrip.addView(cell,lp);
            TextView title=label(t.title,12,NAVY);
            title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setPadding(dp(8),0,0,0);
            cell.addView(title,new LinearLayout.LayoutParams(0,-1,1));
            title.setOnClickListener(v->{if(!p.selected.equals(t.id)){saveEverything();p.selected=t.id;showTab();}});
            TextView close=label("×",22,0xff697991);close.setGravity(Gravity.CENTER);
            cell.addView(close,new LinearLayout.LayoutParams(dp(34),-1));
            close.setOnClickListener(v->closeTab(t));
        }
        Button add=btn("+ Tab");tabStrip.addView(add,new LinearLayout.LayoutParams(dp(72),dp(42)));
        add.setOnClickListener(v->{saveEverything();Tab t=new Tab();p.tabs.add(t);p.selected=t.id;showTab();});
    }
    void closeTab(Tab t){
        Profile p=profile();
        if(t.web!=null){
            if(t.web.getParent() instanceof ViewGroup)((ViewGroup)t.web.getParent()).removeView(t.web);
            t.web.destroy();t.web=null;
        }
        stateFile(t).delete();p.tabs.remove(t);
        if(p.tabs.isEmpty())p.tabs.add(new Tab());
        if(p.selected.equals(t.id))p.selected=p.tabs.get(p.tabs.size()-1).id;
        showTab();
    }

    WebView createWeb(Tab t,Profile p){
        WebView w=new WebView(this);
        if(isolated)WebViewCompat.setProfile(w,p.id);
        WebSettings s=w.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        t.web=w;setDesktop(t);
        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView web,WebResourceRequest req){
                if(!req.isForMainFrame())return false;
                Uri u=req.getUrl();String scheme=u.getScheme();
                if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)){
                    if("accounts.google.com".equalsIgnoreCase(u.getHost())&&!googleMessageShown){
                        googleMessageShown=true;
                        String link=u.toString();
                        new AlertDialog.Builder(MainActivity.this).setTitle("Google sign-in")
                            .setMessage("Google may block sign-in inside an embedded browser. Opening in Chrome is safer, but Chrome login cookies cannot return to this separate profile.")
                            .setNegativeButton("Continue here",(d,x)->web.loadUrl(link))
                            .setPositiveButton("Open Chrome",(d,x)->openChrome(link)).show();
                        return true;
                    }
                    return false;
                }
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){}
                return true;
            }
            @Override public void onPageStarted(WebView web,String url,android.graphics.Bitmap favicon){
                if(url!=null && url.startsWith("http"))t.url=url;
                if(active(t))address.setText(t.url);
                saveMetadata();
            }
            @Override public void onPageFinished(WebView web,String url){
                if(url!=null && url.startsWith("http"))t.url=url;
                if(active(t))address.setText(t.url);
                saveMetadata();
            }
        });
        w.setWebChromeClient(new WebChromeClient(){
            @Override public void onReceivedTitle(WebView web,String title){
                if(title!=null&&!title.trim().isEmpty()){
                    t.title=title;if(active(t))renderTabs();saveMetadata();
                }
            }
            @Override public void onProgressChanged(WebView web,int progress){
                if(active(t))status.setText(progress==100?"Session saved locally":"Loading "+progress+"%");
            }
            @Override public boolean onShowFileChooser(WebView web,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(uploadCallback!=null)uploadCallback.onReceiveValue(null);
                uploadCallback=cb;
                try{startActivityForResult(params.createIntent(),PICK_FILE);return true;}
                catch(Exception e){uploadCallback=null;return false;}
            }
        });
        w.setDownloadListener((url,ua,disposition,mime,size)->{
            try{
                DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url,disposition,mime));
                String cookie=isolated?WebViewCompat.getProfile(w).getCookieManager().getCookie(url)
                    :CookieManager.getInstance().getCookie(url);
                if(cookie!=null)req.addRequestHeader("Cookie",cookie);
                if(ua!=null)req.addRequestHeader("User-Agent",ua);
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this,"Download started",0).show();
            }catch(Exception e){Toast.makeText(this,"Download unavailable",0).show();}
        });
        if(!restore(t,w))w.loadUrl(t.url);
        return w;
    }
    void navigate(){
        String q=address.getText().toString().trim();if(q.isEmpty())return;
        String url=q;
        if(!q.matches("(?i)^https?://.+")){
            if(q.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}([/:?#].*)?$"))url="https://"+q;
            else try{url="https://www.google.com/search?q="+URLEncoder.encode(q,"UTF-8");}
                catch(Exception e){url="https://www.google.com/";}
        }
        Tab t=currentTab();t.url=url;t.web.loadUrl(url);saveMetadata();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        address.clearFocus();
    }
    void setDesktop(Tab t){
        if(t.web==null)return;
        t.web.getSettings().setUserAgentString(t.desktop?
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36":null);
    }
    void toggleDesktop(){
        Tab t=currentTab();t.desktop=!t.desktop;setDesktop(t);
        desktopButton.setText(t.desktop?"Desktop on":"Desktop off");
        saveMetadata();if(t.web!=null)t.web.reload();
    }
    void openChrome(String url){
        if(url==null||!url.startsWith("http"))return;saveEverything();
        try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(i);}
        catch(Exception e){Toast.makeText(this,"No browser installed",0).show();}
    }

    File stateFile(Tab t){return new File(getFilesDir(),"tab_"+t.id+".bin");}
    void snapshot(Tab t){
        if(t.web==null)return;
        Parcel parcel=null;
        try{
            Bundle bundle=new Bundle();
            if(t.web.saveState(bundle)==null)return;
            parcel=Parcel.obtain();bundle.writeToParcel(parcel,0);
            byte[] bytes=parcel.marshall();if(bytes.length>4000000)return;
            File file=new File(getFilesDir(),"tab_"+t.id+".tmp");
            try(FileOutputStream out=new FileOutputStream(file)){
                out.write(bytes);out.getFD().sync();
            }
            if(!file.renameTo(stateFile(t)))file.delete();
        }catch(Exception e){}finally{if(parcel!=null)parcel.recycle();}
    }
    boolean restore(Tab t,WebView w){
        File f=stateFile(t);if(!f.exists()||f.length()>4000000)return false;
        Parcel parcel=null;
        try(FileInputStream in=new FileInputStream(f);
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            byte[] bytes=out.toByteArray();parcel=Parcel.obtain();
            parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);
            Bundle state=Bundle.CREATOR.createFromParcel(parcel);
            state.setClassLoader(getClass().getClassLoader());
            return w.restoreState(state)!=null;
        }catch(Exception e){return false;}
        finally{if(parcel!=null)parcel.recycle();}
    }
    void saveEverything(){
        for(Profile p:profiles)for(Tab t:p.tabs){
            if(t.web!=null){
                String url=t.web.getUrl();
                if(url!=null&&url.startsWith("http"))t.url=url;
                snapshot(t);
            }
        }
        saveMetadata();
        if(isolated)for(Profile p:profiles)for(Tab t:p.tabs)
            if(t.web!=null)WebViewCompat.getProfile(t.web).getCookieManager().flush();
        else CookieManager.getInstance().flush();
    }
    void saveMetadata(){
        try{
            JSONObject root=new JSONObject();root.put("selectedProfile",selectedProfile);
            JSONArray pa=new JSONArray();
            for(Profile p:profiles){
                JSONObject po=new JSONObject();
                po.put("id",p.id);po.put("name",p.name);po.put("selected",p.selected);
                JSONArray ta=new JSONArray();
                for(Tab t:p.tabs){
                    JSONObject to=new JSONObject();
                    to.put("id",t.id);to.put("url",t.url);to.put("title",t.title);
                    to.put("desktop",t.desktop);ta.put(to);
                }
                po.put("tabs",ta);pa.put(po);
            }
            root.put("profiles",pa);
            getSharedPreferences("state",0).edit().putString("workspace",root.toString()).commit();
        }catch(Exception e){}
    }
    void load(){
        try{
            String raw=getSharedPreferences("state",0).getString("workspace","");
            if(!raw.isEmpty()){
                JSONObject root=new JSONObject(raw);
                selectedProfile=root.optString("selectedProfile","");
                JSONArray pa=root.optJSONArray("profiles");
                if(pa!=null)for(int i=0;i<pa.length();i++){
                    JSONObject po=pa.getJSONObject(i);Profile p=new Profile();p.tabs.clear();
                    p.id=po.getString("id");p.name=po.optString("name","Profile");
                    p.selected=po.optString("selected","");
                    JSONArray ta=po.optJSONArray("tabs");
                    if(ta!=null)for(int j=0;j<ta.length();j++){
                        JSONObject to=ta.getJSONObject(j);Tab t=new Tab();
                        t.id=to.getString("id");t.url=to.optString("url",t.url);
                        t.title=to.optString("title",t.title);t.desktop=to.optBoolean("desktop",false);
                        p.tabs.add(t);
                    }
                    if(p.tabs.isEmpty())p.tabs.add(new Tab());
                    if(p.selected.isEmpty())p.selected=p.tabs.get(0).id;
                    profiles.add(p);
                }
            }
        }catch(Exception e){profiles.clear();}
        if(profiles.isEmpty()){Profile p=new Profile();p.name="Personal";profiles.add(p);selectedProfile=p.id;}
        if(!isolated)selectedProfile=profiles.get(0).id;
    }
    @Override protected void onPause(){saveEverything();super.onPause();}
    @Override protected void onStop(){saveEverything();super.onStop();}
    @Override public void onBackPressed(){
        WebView w=currentWeb();if(w!=null&&w.canGoBack())w.goBack();
        else moveTaskToBack(true);
    }
    @Override protected void onActivityResult(int req,int code,Intent data){
        super.onActivityResult(req,code,data);
        if(req==PICK_FILE&&uploadCallback!=null){
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(code,data));
            uploadCallback=null;
        }
    }
}
