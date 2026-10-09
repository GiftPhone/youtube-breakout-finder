package com.profilekeeper.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextWatcher;
import android.text.Editable;
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
    static final int NAVY = Color.rgb(18,31,55), BLUE = Color.rgb(29,115,229);
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
    boolean isolated=false, rebuilding=false, googleMessageShown=false, inBrowser=false;
    FrameLayout dashboardFrame;
    LinearLayout browserRoot;
    String profileFilter="";
    int nextChromeNumber=1;
    Spinner profileSelect;
    EditText address;
    LinearLayout tabStrip;
    HorizontalScrollView tabScroll;
    FrameLayout browserFrame;
    Button desktopButton;
    TextView browserTitle;
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
        if(inBrowser)showTab();else showDashboard();
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


    GradientDrawable rounded(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));
        return d;
    }
    class ChromeIcon extends View {
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ChromeIcon(){super(MainActivity.this);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float width=getWidth(),height=getHeight(),r=Math.min(width,height)*.48f;
            float cx=width/2f,cy=height/2f;
            RectF rect=new RectF(cx-r,cy-r,cx+r,cy+r);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffea4335);c.drawArc(rect,-90,120,true,paint);
            paint.setColor(0xfffbbc05);c.drawArc(rect,30,120,true,paint);
            paint.setColor(0xff34a853);c.drawArc(rect,150,120,true,paint);
            paint.setColor(-1);c.drawCircle(cx,cy,r*.51f,paint);
            paint.setColor(0xff4285f4);c.drawCircle(cx,cy,r*.43f,paint);
        }
    }
    TextView boldText(String text,int size,int color){
        TextView v=label(text,size,color);v.setTypeface(null,1);return v;
    }
    void showDashboard(){
        saveEverything();
        inBrowser=false;
        browserRoot.setVisibility(View.GONE);
        dashboardFrame.setVisibility(View.VISIBLE);
        refreshDashboard();
        saveMetadata();
    }
    void refreshDashboard(){
        dashboardFrame.removeAllViews();
        dashboardFrame.addView(createDashboard(),new FrameLayout.LayoutParams(-1,-1));
    }
    View createDashboard(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff6f7f9);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(1);
        page.setPadding(dp(18),dp(12),dp(18),dp(24));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        TextView title=boldText("Chrome Profile Generator",27,0xff1e2023);
        page.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView subtitle=label("Create separate browser sessions on this phone.",14,0xff74777d);
        LinearLayout.LayoutParams sublp=new LinearLayout.LayoutParams(-1,-2);
        sublp.setMargins(0,dp(5),0,dp(16));page.addView(subtitle,sublp);
        TextView support=label(isolated?
            "✓ Separate browser profiles supported on this device":
            "⚠ Separate browser profiles not supported on this device",13,
            isolated?0xff227346:0xff8b5312);
        support.setBackground(rounded(isolated?0xffe5f4e9:0xfffff0d2,12));
        support.setPadding(dp(12),dp(12),dp(12),dp(12));
        page.addView(support,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout generator=row();
        generator.setPadding(dp(12),dp(12),dp(12),dp(12));
        generator.setBackground(rounded(-1,19));
        LinearLayout.LayoutParams genLp=new LinearLayout.LayoutParams(-1,dp(80));
        genLp.setMargins(0,dp(13),0,dp(22));page.addView(generator,genLp);
        EditText count=new EditText(this);
        count.setSingleLine(true);
        count.setText("10");
        count.setTextSize(19);
        count.setPadding(dp(15),0,dp(10),0);
        count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        count.setBackground(rounded(0xfff1f3f7,12));
        LinearLayout.LayoutParams countLp=new LinearLayout.LayoutParams(0,-1,1.5f);
        countLp.setMargins(0,0,dp(10),0);generator.addView(count,countLp);
        TextView generate=boldText("Generate",16,-1);
        generate.setGravity(Gravity.CENTER);
        generate.setBackground(rounded(BLUE,12));
        generator.addView(generate,new LinearLayout.LayoutParams(0,-1,1));
        generate.setOnClickListener(v->{
            int n;
            try{n=Integer.parseInt(count.getText().toString());}catch(Exception e){n=0;}
            if(n<1||n>100){
                Toast.makeText(this,"Enter a number from 1 to 100",Toast.LENGTH_LONG).show();
                return;
            }
            generateChromeProfiles(n);
        });
        LinearLayout stats=row();
        LinearLayout.LayoutParams statsLp=new LinearLayout.LayoutParams(-1,dp(34));
        statsLp.setMargins(0,0,0,dp(8));page.addView(stats,statsLp);
        TextView total=boldText(profiles.size()+" Profiles",19,0xff252529);
        stats.addView(total,new LinearLayout.LayoutParams(0,-1,1));
        TextView info=label("Hold a card to delete",12,0xff84878e);
        stats.addView(info,new LinearLayout.LayoutParams(-2,-1));
        EditText search=new EditText(this);
        search.setSingleLine(true);search.setTextSize(14);
        search.setHint("Search Chrome 1, Chrome 25...");
        search.setPadding(dp(15),0,dp(15),0);
        search.setBackground(rounded(-1,17));
        search.setText(profileFilter);
        page.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout grid=new LinearLayout(this);
        grid.setOrientation(1);
        page.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        renderCards(grid);
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int co,int af){}
            @Override public void onTextChanged(CharSequence s,int st,int be,int co){
                profileFilter=s.toString();renderCards(grid);
            }
            @Override public void afterTextChanged(Editable e){}
        });
        return scroll;
    }
    void generateChromeProfiles(int count){
        if(!isolated){Toast.makeText(this,"Update Android System WebView to enable separate profiles",Toast.LENGTH_LONG).show();return;}
        if(profiles.size()+count>250){
            Toast.makeText(this,"A maximum of 250 profiles is supported",Toast.LENGTH_LONG).show();
            return;
        }
        for(int i=0;i<count;i++){
            Profile p=new Profile();
            p.name="Chrome "+nextChromeNumber++;
            profiles.add(p);
        }
        saveMetadata();
        refreshDashboard();
    }
    void renderCards(LinearLayout grid){
        grid.removeAllViews();
        ArrayList<Profile> visible=new ArrayList<>();
        String filter=profileFilter.trim().toLowerCase(Locale.ROOT);
        for(Profile p:profiles)if(p.name.toLowerCase(Locale.ROOT).contains(filter))visible.add(p);
        for(int i=0;i<visible.size();i+=2){
            LinearLayout line=row();
            grid.addView(line,new LinearLayout.LayoutParams(-1,dp(143)));
            for(int col=0;col<2;col++){
                int k=i+col;
                if(k>=visible.size()){
                    View blank=new View(this);
                    line.addView(blank,new LinearLayout.LayoutParams(0,-1,1));continue;
                }
                Profile p=visible.get(k);
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(1);card.setGravity(Gravity.CENTER);
                card.setBackground(rounded(-1,19));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(132),1);
                lp.setMargins(col==0?0:dp(5),dp(6),col==0?dp(5):0,dp(5));
                line.addView(card,lp);
                ChromeIcon icon=new ChromeIcon();
                card.addView(icon,new LinearLayout.LayoutParams(dp(62),dp(62)));
                TextView name=boldText(p.name,16,0xff212329);
                name.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(-1,dp(24));
                nameLp.setMargins(0,dp(3),0,0);card.addView(name,nameLp);
                TextView tap=label("Tap to open",12,0xff85888e);
                tap.setGravity(Gravity.CENTER);
                card.addView(tap,new LinearLayout.LayoutParams(-1,dp(22)));
                card.setOnClickListener(v->{
                    saveEverything();selectedProfile=p.id;updateProfileMenu();showTab();
                });
                card.setOnLongClickListener(v->{
                    confirmDelete(p);return true;
                });
            }
        }
    }
    void confirmDelete(Profile p){
        new AlertDialog.Builder(this)
            .setTitle("Delete "+p.name+"?")
            .setMessage("This permanently deletes this profile's saved tabs. Continue?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{
                if(profiles.size()<=1){
                    Toast.makeText(this,"Keep at least one profile",Toast.LENGTH_SHORT).show();return;
                }
                for(Tab t:p.tabs){
                    if(t.web!=null){if(t.web.getParent() instanceof ViewGroup)
                        ((ViewGroup)t.web.getParent()).removeView(t.web);
                        t.web.destroy();t.web=null;}
                    stateFile(t).delete();
                }
                profiles.remove(p);
                if(selectedProfile.equals(p.id))selectedProfile=profiles.get(0).id;
                saveMetadata();refreshDashboard();
            }).show();
    }


    TextView actionLabel(String text,int size,int background,int foreground){
        TextView v=boldText(text,size,foreground);
        v.setGravity(Gravity.CENTER);
        v.setBackground(rounded(background,12));
        return v;
    }
    void displayTitle(){
        if(browserTitle==null)return;
        Profile p=profile();
        Tab t=currentTab();
        String title=t.title==null||t.title.trim().isEmpty()?"New tab":t.title.trim();
        if(title.length()>18)title=title.substring(0,18)+"…";
        browserTitle.setText(p.name+" - "+title);
    }
    void drawUi(){
        FrameLayout shell=new FrameLayout(this);
        setContentView(shell);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(1);
        root.setBackgroundColor(0xfff7f8fa);
        browserRoot=root;
        shell.addView(root,new FrameLayout.LayoutParams(-1,-1));
        dashboardFrame=new FrameLayout(this);
        shell.addView(dashboardFrame,new FrameLayout.LayoutParams(-1,-1));

        // First row: selected profile + page title and the isolation indicator.
        LinearLayout top=row();
        top.setPadding(dp(12),dp(3),dp(12),dp(3));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView exit=actionLabel("‹",27,0xffe8edf5,0xff202b3c);
        top.addView(exit,new LinearLayout.LayoutParams(dp(37),dp(42)));
        exit.setOnClickListener(v->showDashboard());
        browserTitle=boldText("Chrome 1 - Google",19,0xff202124);
        browserTitle.setSingleLine(true);
        browserTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-1,1);
        titleLp.setMargins(dp(8),0,dp(5),0);
        top.addView(browserTitle,titleLp);
        TextView isolation=label(isolated?"Isolated":"Shared",12,isolated?0xff176936:0xff835a10);
        isolation.setGravity(Gravity.CENTER);
        isolation.setBackground(rounded(isolated?0xffe5f3e9:0xffffedcc,16));
        top.addView(isolation,new LinearLayout.LayoutParams(dp(76),dp(36)));

        // Tabs are always visible. + opens a TAB; P+ opens profile previews.
        LinearLayout tabsRow=row();
        tabsRow.setPadding(dp(8),dp(3),dp(8),dp(3));
        root.addView(tabsRow,new LinearLayout.LayoutParams(-1,dp(62)));
        tabScroll=new HorizontalScrollView(this);
        tabScroll.setFillViewport(false);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabsRow.addView(tabScroll,new LinearLayout.LayoutParams(0,-1,1));
        tabStrip=row();
        tabScroll.addView(tabStrip,new android.widget.FrameLayout.LayoutParams(-2,-1));
        TextView addTab=actionLabel("+",24,BLUE,-1);
        LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(dp(49),dp(48));
        addLp.setMargins(dp(7),0,dp(5),0);
        tabsRow.addView(addTab,addLp);
        addTab.setOnClickListener(v->{
            Profile p=profile();
            saveEverything();
            Tab t=new Tab();p.tabs.add(t);p.selected=t.id;
            showTab();
        });
        TextView profilesBtn=actionLabel("P+",18,0xff31a452,-1);
        tabsRow.addView(profilesBtn,new LinearLayout.LayoutParams(dp(51),dp(48)));
        profilesBtn.setOnClickListener(v->showProfilePreview());

        LinearLayout location=row();
        location.setPadding(dp(10),dp(4),dp(10),dp(3));
        root.addView(location,new LinearLayout.LayoutParams(-1,dp(65)));
        address=new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setSelectAllOnFocus(true);
        address.setHint("Search or enter an address");
        address.setPadding(dp(15),0,dp(12),0);
        address.setBackground(rounded(0xffeceef2,18));
        LinearLayout.LayoutParams addressLp=new LinearLayout.LayoutParams(0,dp(54),1);
        addressLp.setMargins(0,0,dp(8),0);
        location.addView(address,addressLp);
        address.setOnEditorActionListener((v,a,e)->{navigate();return true;});
        TextView go=actionLabel("Go",17,BLUE,-1);
        location.addView(go,new LinearLayout.LayoutParams(dp(65),dp(54)));
        go.setOnClickListener(v->navigate());

        LinearLayout controls=row();
        controls.setPadding(dp(10),dp(3),dp(10),dp(5));
        root.addView(controls,new LinearLayout.LayoutParams(-1,dp(55)));
        String[] labels={"‹","›","↻","⌂"};
        for(int i=0;i<labels.length;i++){
            final int n=i;
            TextView ctl=actionLabel(labels[i],22,0xffeef0f4,0xff31363e);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
            lp.setMargins(0,0,dp(5),0);
            controls.addView(ctl,lp);
            ctl.setOnClickListener(v->{
                WebView web=currentWeb();
                if(web==null)return;
                if(n==0&&web.canGoBack())web.goBack();
                if(n==1&&web.canGoForward())web.goForward();
                if(n==2)web.reload();
                if(n==3)web.loadUrl("https://www.google.com/");
            });
        }
        desktopButton=btn("Desktop OFF");
        desktopButton.setAllCaps(false);
        desktopButton.setTextSize(11);
        desktopButton.setTextColor(BLUE);
        desktopButton.setBackground(rounded(0xffe6eefc,12));
        controls.addView(desktopButton,new LinearLayout.LayoutParams(dp(104),dp(44)));
        desktopButton.setOnClickListener(v->toggleDesktop());

        browserFrame=new FrameLayout(this);
        browserFrame.setBackgroundColor(-1);
        root.addView(browserFrame,new LinearLayout.LayoutParams(-1,0,1));
        status=label("Ready",10,0xff787e89);
        status.setPadding(dp(12),0,dp(6),0);
        status.setBackgroundColor(0xfff7f8fa);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(20)));
        profileSelect=new Spinner(this);
    }
    void showProfilePreview(){
        saveEverything();
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(false);
        LinearLayout grid=new LinearLayout(this);
        grid.setOrientation(1);
        grid.setPadding(dp(12),dp(8),dp(12),dp(8));
        sc.addView(grid,new ScrollView.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Switch Chrome profile")
            .setView(sc)
            .setNeutralButton("All profiles",(d,w)->showDashboard())
            .setNegativeButton("Close",null).create();
        for(int i=0;i<profiles.size();i+=2){
            LinearLayout row=row();
            grid.addView(row,new LinearLayout.LayoutParams(-1,dp(116)));
            for(int j=0;j<2;j++){
                int index=i+j;
                if(index>=profiles.size()){
                    row.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1));continue;
                }
                Profile p=profiles.get(index);
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(1);
                card.setGravity(Gravity.CENTER);
                card.setBackground(rounded(p.id.equals(selectedProfile)?0xffdfebff:0xfff4f5f8,14));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(108),1);
                cp.setMargins(dp(4),dp(4),dp(4),dp(4));
                row.addView(card,cp);
                ChromeIcon icon=new ChromeIcon();
                card.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
                TextView name=boldText(p.name,14,0xff22252a);
                name.setGravity(Gravity.CENTER);
                card.addView(name,new LinearLayout.LayoutParams(-1,dp(24)));
                TextView count=label(p.tabs.size()+" tab"+(p.tabs.size()==1?"":"s"),11,0xff696f7a);
                count.setGravity(Gravity.CENTER);
                card.addView(count,new LinearLayout.LayoutParams(-1,dp(20)));
                card.setOnClickListener(v->{
                    if(!isolated&&!p.id.equals(selectedProfile)){
                        Toast.makeText(this,"Separate profiles unavailable on this device",Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveEverything();
                    selectedProfile=p.id;
                    updateProfileMenu();
                    dialog.dismiss();
                    showTab();
                });
            }
        }
        dialog.show();
    }

    Profile profile(){
        if(profiles.isEmpty()){Profile p=new Profile();p.name="Chrome 1";profiles.add(p);selectedProfile=p.id;}
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
        inBrowser=true;
        dashboardFrame.setVisibility(View.GONE);
        browserRoot.setVisibility(View.VISIBLE);
        Tab t=currentTab();
        browserFrame.removeAllViews();
        if(t.web==null)t.web=createWeb(t,profile());
        if(t.web.getParent() instanceof ViewGroup)((ViewGroup)t.web.getParent()).removeView(t.web);
        browserFrame.addView(t.web,new FrameLayout.LayoutParams(-1,-1));
        address.setText(t.url);
        desktopButton.setText(t.desktop?"Desktop ON":"Desktop OFF");
        displayTitle();
        renderTabs();saveMetadata();
    }

    void renderTabs(){
        tabStrip.removeAllViews();
        Profile p=profile();
        displayTitle();
        int index=0,activeIndex=0;
        for(Tab t:p.tabs){
            if(t.id.equals(p.selected))activeIndex=index;
            LinearLayout cell=row();
            cell.setPadding(dp(8),0,dp(1),0);
            cell.setBackground(rounded(t.id.equals(p.selected)?-1:0xffe5e7ed,13));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(144),dp(45));
            lp.setMargins(dp(2),0,dp(4),0);
            tabStrip.addView(cell,lp);
            TextView title=label(t.title,12,t.id.equals(p.selected)?BLUE:0xff30353f);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(title,new LinearLayout.LayoutParams(0,-1,1));
            title.setOnClickListener(v->{
                if(!p.selected.equals(t.id)){
                    saveEverything();
                    p.selected=t.id;
                    showTab();
                }
            });
            TextView close=label("×",23,0xff717780);
            close.setGravity(Gravity.CENTER);
            cell.addView(close,new LinearLayout.LayoutParams(dp(30),-1));
            close.setOnClickListener(v->closeTab(t));
            index++;
        }
        final int target=activeIndex;
        if(tabScroll!=null)tabScroll.post(()->tabScroll.smoothScrollTo(dp(target*150),0));
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
                    // Allow Google navigation to remain in this tab. Authentication limitations
                    // are decided by Google and cannot be bypassed by this WebView.
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
                    t.title=title;
                    if(active(t)&&inBrowser)renderTabs();
                    saveMetadata();
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
        desktopButton.setText(t.desktop?"Desktop ON":"Desktop OFF");
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
        if(isolated){
            for(Profile p:profiles)for(Tab t:p.tabs)
                if(t.web!=null)WebViewCompat.getProfile(t.web).getCookieManager().flush();
        }else CookieManager.getInstance().flush();
    }
    void saveMetadata(){
        try{
            JSONObject root=new JSONObject();root.put("selectedProfile",selectedProfile);
            root.put("inBrowser",inBrowser);
            root.put("nextChromeNumber",nextChromeNumber);
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
                inBrowser=root.optBoolean("inBrowser",false);
                nextChromeNumber=root.optInt("nextChromeNumber",1);
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
        if(profiles.isEmpty()){Profile p=new Profile();p.name="Chrome 1";profiles.add(p);selectedProfile=p.id;nextChromeNumber=2;}
        for(Profile p:profiles){
            if(p.name.startsWith("Chrome "))try{
                int n=Integer.parseInt(p.name.substring(7).trim());
                nextChromeNumber=Math.max(nextChromeNumber,n+1);
            }catch(Exception ignored){}
        }
        if(!isolated)selectedProfile=profiles.get(0).id;
    }
    @Override protected void onSaveInstanceState(Bundle out){
        saveEverything();
        out.putString("selectedProfile",selectedProfile);
        out.putString("selectedTab",profile().selected);
        out.putBoolean("inBrowser",inBrowser);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause(){saveEverything();super.onPause();}
    @Override protected void onStop(){saveEverything();super.onStop();}
    @Override public void onBackPressed(){
        if(!inBrowser){super.onBackPressed();return;}
        WebView w=currentWeb();if(w!=null&&w.canGoBack())w.goBack();
        else showDashboard();
    }
    @Override protected void onActivityResult(int req,int code,Intent data){
        super.onActivityResult(req,code,data);
        if(req==PICK_FILE&&uploadCallback!=null){
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(code,data));
            uploadCallback=null;
        }
    }
}
