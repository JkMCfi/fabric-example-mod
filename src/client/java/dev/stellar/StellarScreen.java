package dev.stellar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.FileDialog;
import java.awt.Frame;
import java.util.List;
import java.util.Map;

/** Valence-inspired split player/settings interface on a 1280x720 virtual canvas. */
public final class StellarScreen extends Screen {
    private static final int PURPLE = 0xFF8C5BEB;
    private static final int DARK = 0xE616131E;
    private static final String[] TABS = {"Overview", "Skies", "Lyrics", "Styles", "HUD", "Performance", "Settings", "Information"};
    private static final String[] FEATURES = {"Skies", "Lyrics", "Styles", "Performance", "HUD", "Settings"};
    private final SpotifyService music;
    private String tab = "Overview";
    private String clientIdInput = "";
    private String searchInput = "";
    private boolean searchFocused;
    private boolean clientIdFocused;
    private float scale, offsetX, offsetY;
    private long openedAt;

    public StellarScreen(SpotifyService music) {
        super(Text.literal("Stellar")); this.music = music; this.clientIdInput = music.clientId();
    }

    private void metrics() {
        scale = Math.min(width / 1280.0f, height / 720.0f);
        offsetX = (width - 1280 * scale) / 2; offsetY = (height - 720 * scale) / 2;
    }
    private int sx(float x) { return Math.round(offsetX + x * scale); }
    private int sy(float y) { return Math.round(offsetY + y * scale); }
    private int ss(float size) { return Math.max(1, Math.round(size * scale)); }
    private void rect(DrawContext c, float x, float y, float w, float h, int color) { c.fill(sx(x), sy(y), sx(x+w), sy(y+h), color); }
    private void round(DrawContext c, float x, float y, float w, float h, float radius, int color) {
        int rows = Math.max(1, ss(h)); float logicalRadius = Math.min(radius, Math.min(w, h) / 2);
        for (int row = 0; row < rows; row++) {
            float ly = (row + .5f) / scale; float inset = 0;
            if (ly < logicalRadius) inset = logicalRadius - (float)Math.sqrt(Math.max(0, logicalRadius*logicalRadius - (logicalRadius-ly)*(logicalRadius-ly)));
            else if (ly > h-logicalRadius) { float d=ly-(h-logicalRadius); inset=logicalRadius-(float)Math.sqrt(Math.max(0,logicalRadius*logicalRadius-d*d)); }
            c.fill(sx(x+inset), sy(y)+row, sx(x+w-inset), sy(y)+row+1, color);
        }
    }
    private void text(DrawContext c, String value, float x, float y, int color) { c.drawTextWithShadow(textRenderer, value, sx(x), sy(y), color); }
    private boolean hit(double mouseX, double mouseY, float x, float y, float w, float h) {
        float vx = (float)((mouseX-offsetX)/scale), vy=(float)((mouseY-offsetY)/scale);
        return vx>=x && vx<=x+w && vy>=y && vy<=y+h;
    }
    private void title(DrawContext c, String value, String subtitle) {
        rect(c, 381, 35, 4, 42, PURPLE); text(c, value, 400, 37, 0xFFFFFFFF); text(c, subtitle, 400, 54, 0xFFAAA5B8);
        text(c, "Your game.", 1065, 37, 0xFFFFFFFF); text(c, "Our extension", 1065, 51, 0xFFAAA5B8);
        round(c, 1207, 32, 32, 32, 16, 0xFF302A3B); text(c, "×", 1218, 39, 0xFFFFFFFF);
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        metrics(); context.fill(0,0,width,height,0x9906040D);
        // Independent left floating player
        round(context, 22, 38, 315, 640, 18, 0xEE100E16);
        round(context, 28, 44, 303, 628, 14, 0xFF15121B);
        var track=music.track();
        // Album artwork placeholder uses Spotify metadata accent until an image texture is available.
        round(context, 47, 60, 140, 140, 12, 0xFF49366E);
        text(context, "✦", 91, 100, 0xFFFFFFFF); text(context, "STELLAR", 74, 128, 0xFFE7D8FF);
        // Record peeking from cover edge
        round(context, 169, 72, 87, 116, 50, 0xFF08080C);
        for (int i=0;i<5;i++) round(context, 179+i*3, 83+i*3, 67-i*6, 94-i*6, 32, 0xFF17151C);
        round(context, 201, 121, 24, 24, 12, 0xFF7552A9);
        text(context, track.title(), 48, 216, 0xFFFFFFFF); text(context, track.artist(), 48, 235, 0xFFAAA5B8);
        Map.Entry<Long,String> lyric=music.lyricAt(track.progressMs());
        text(context, lyric == null ? "Lyrics will appear here" : lyric.getValue(), 48, 269, 0xFFECE8F3);
        var lyrics=music.lyrics().entrySet().stream().skip(Math.max(0,music.lyrics().headMap(track.progressMs()).size())).limit(2).toList();
        if (!lyrics.isEmpty()) text(context, lyrics.get(0).getValue(), 48, 287, 0xFF8D8797);
        // spectrum / playback controls
        for(int i=0;i<32;i++){ int h=5+(int)(Math.abs(Math.sin((i+System.currentTimeMillis()/150.0)*.6))*24); rect(context,48+i*7,514-h,4,h,0xFFE8E3ED); }
        text(context,"◀",76,553,0xFFDCD7E5); round(context,115,540,42,42,21,0xFFF2F0F6);
        text(context,track.playing()?"Ⅱ":"▶",129,553,0xFF211A2D); text(context,"▶",180,553,0xFFDCD7E5); text(context,"↻",236,553,0xFFDCD7E5);
        text(context,music.status(),48,603,0xFF9B95A5);

        // Main glass panel/sidebar
        round(context, 351, 22, 907, 676, 18, DARK); round(context, 351,22,907,1,1,0x44FFFFFF);
        round(context, 351,22,210,676,18,0xF0120F1A); rect(context,552,39,1,643,0x22FFFFFF);
        round(context,370,39,28,28,7,PURPLE); text(context,"✦",378,46,0xFFFFFFFF); text(context,"Stellar",406,41,0xFFFFFFFF); text(context,"STELLAR MOD",406,56,0xFF91899D);
        for(int i=0;i<TABS.length;i++){int y=91+i*43; if(tab.equals(TABS[i])) round(context,367,y,176,32,8,0xFF7B4CE2);
            text(context,new String[]{"◈","☼","♫","✧","▣","◉","⚙","ⓘ"}[i],378,y+9,0xFFDED5F0); text(context,TABS[i],398,y+9,0xFFFFFFFF); }
        text(context,"Stellar v1.0.0",377,663,0xFFB8A8D8); text(context,"Fabric · Minecraft 1.21.11",377,678,0xFF777180);
        title(context, tab, subtitleFor(tab));
        if(tab.equals("Overview")) drawOverview(context,track);
        else drawTab(context,track);
        rect(context, 633, 676, 342, 1, 0x448F8998); text(context,"discord.gg/stellarmod",975,667,0xFFB8A8D8); rect(context, 1112,676,105,1,0x448F8998);
        super.render(context,mouseX,mouseY,delta);
    }
    private String subtitleFor(String name) { return switch(name){case "Overview"->"Welcome to Valence";case "Settings"->"Connect your music";case "Lyrics"->"Synced to what you hear";default->"Personalize your experience";}; }
    private void drawOverview(DrawContext c, SpotifyService.Track track) {
        round(c,575,94,650,126,12,0xFF201A2B); for(int i=0;i<26;i++) round(c,585+(i*53%620),101+(i*31%97),2,2,1,0xFFAFA1D3);
        text(c,"✦  Valence",597,108,0xFFFFFFFF); text(c,"Make the night just a mod.",597,128,0xFFC9C2D3);
        // Saturn hero illustration: orbital ellipses and sphere built from soft pixel rows.
        for(int i=0;i<5;i++) round(c,1041-i*4,111+i*4,145+i*8,49-i*6,26,0xFF675342);
        round(c,1081,106,108,84,42,0xFFE1C99A); round(c,1097,119,70,58,29,0xFFEED9AA); round(c,1110,133,44,31,20,0xFFD0B685);
        for(int i=0;i<6;i++) round(c,586+(i%3)*214,233+(i/3)*104,202,91,9,0xFF201D28);
        for(int i=0;i<6;i++){int x=600+(i%3)*214,y=245+(i/3)*104; round(c,x,y,18,18,6,PURPLE); text(c,"✧",x+5,y+4,0xFFFFFFFF); text(c,FEATURES[i],x+27,y+3,0xFFFFFFFF);
            text(c,new String[]{"Explore custom skies","Lyrics that follow the beat","Style your interface","Smooth, lightweight playback","A HUD that fits your world","Tune every detail"}[i],x,y+34,0xFFAAA4B4); text(c,"↗",x+177,y+62,0xFFB8A8D8); }
        text(c,"Now playing",575,452,0xFFAAA4B4); round(c,575,470,650,71,10,0xFF201D28);
        round(c,588,480,48,48,7,0xFF51426D); text(c,"♫",602,494,0xFFFFFFFF); text(c,track.title(),646,480,0xFFFFFFFF); text(c,track.artist(),646,496,0xFFAAA4B4); text(c,"• Lyrics synced",646,515,0xFF77D89A);
        text(c,time(track.progressMs())+" / "+time(track.durationMs()),901,490,0xFFAAA4B4); text(c,"◀   "+(track.playing()?"Ⅱ":"▶")+"   ▶",1039,492,0xFFFFFFFF);
        round(c,589,530,616,3,2,0xFF51465E); round(c,589,530,616*(float)Math.min(1,track.progressMs()/(double)Math.max(1,track.durationMs())),3,2,PURPLE);
    }
    private void drawTab(DrawContext c, SpotifyService.Track track) {
        round(c,575,94,650,560,12,0xAA201D28);
        switch(tab){
            case "Settings" -> { text(c,"Spotify integration",596,115,0xFFFFFFFF); text(c,"Client ID",596,152,0xFFBDB6C8); round(c,596,172,430,34,7,0xFF121018); text(c,clientIdInput.isEmpty()?"Enter Spotify Client ID":clientIdInput,607,183,0xFFFFFFFF);
                button(c,"Connect Spotify",1041,172,153,34); text(c,"Redirect URI: http://127.0.0.1:8888/callback",596,222,0xFFAAA4B4); text(c,music.status(),596,248,0xFFAAA4B4);
                text(c,"Track search",596,300,0xFFFFFFFF); round(c,596,320,430,34,7,0xFF121018); text(c,searchInput.isEmpty()?"Search Spotify tracks…":searchInput,607,331,0xFFFFFFFF); button(c,"Search & play",1041,320,153,34);
                text(c,"OAuth uses PKCE; tokens are stored in config/stellar.json.",596,378,0xFFAAA4B4); }
            case "Lyrics" -> {text(c,"Live lyrics",596,115,0xFFFFFFFF); Map.Entry<Long,String> active=music.lyricAt(track.progressMs()); text(c,active==null?"No synced lyrics loaded":active.getValue(),596,160,0xFFFFFFFF); List<Map.Entry<Long,String>> lines=music.lyrics().entrySet().stream().limit(18).toList(); int y=195; for(var line:lines){text(c,time(line.getKey())+"  "+line.getValue(),596,y,0xFFC1BBCB);y+=22;} button(c,"Import .lrc",596,594,138,34); text(c,"LRCLIB sync enabled · timestamps follow Spotify playback",754,605,0xFFAAA4B4); }
            case "Skies" -> {text(c,"Custom skies",596,115,0xFFFFFFFF); text(c,"Your world, with a little more wonder.",596,148,0xFFAAA4B4); text(c,"Sky themes and render hooks are ready to configure.",596,184,0xFFAAA4B4); button(c,"Enable custom skies",596,222,190,34);}
            case "Styles" -> {text(c,"Interface style",596,115,0xFFFFFFFF);text(c,"Purple glass · Valence-inspired",596,148,0xFFAAA4B4);button(c,"Purple glass",596,184,142,34);button(c,"Midnight",750,184,142,34);}
            default -> {text(c,tab,596,115,0xFFFFFFFF);text(c,"Stellar music companion",596,148,0xFFAAA4B4);text(c,"Minecraft 1.21.11 · Fabric · Java 21",596,177,0xFFAAA4B4);text(c,"Spotify playback and LRCLIB lyrics sync run in the background.",596,205,0xFFAAA4B4);}
        }
    }
    private void button(DrawContext c,String label,int x,int y,int w,int h){round(c,x,y,w,h,7,0xFF7B4CE2);text(c,label,x+12,y+11,0xFFFFFFFF);}
    private static String time(long millis){long s=Math.max(0,millis)/1000;return String.format("%d:%02d",s/60,s%60);}
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button!=0)return super.mouseClicked(mouseX,mouseY,button);
        metrics(); float x=(float)((mouseX-offsetX)/scale), y=(float)((mouseY-offsetY)/scale);
        if(hit(mouseX,mouseY,1207,32,32,32)){close();return true;}
        for(int i=0;i<TABS.length;i++) if(hit(mouseX,mouseY,367,91+i*43,176,32)){tab=TABS[i];return true;}
        if(hit(mouseX,mouseY,115,540,42,42)){music.playPause();return true;}
        if(hit(mouseX,mouseY,48,535,50,48)){music.previous();return true;}
        if(hit(mouseX,mouseY,169,535,48,48)){music.next();return true;}
        if(hit(mouseX,mouseY,225,535,48,48)){music.toggleRepeat();return true;}
        if(tab.equals("Settings")){
            clientIdFocused=hit(mouseX,mouseY,596,172,430,34); searchFocused=hit(mouseX,mouseY,596,320,430,34);
            if(hit(mouseX,mouseY,1041,172,153,34)){music.setClientId(clientIdInput);music.connect();return true;}
            if(hit(mouseX,mouseY,1041,320,153,34)){if(!searchInput.isBlank())music.search(searchInput, result->musicMessage(result));return true;}
        }
        if(tab.equals("Lyrics")&&hit(mouseX,mouseY,596,594,138,34)){openLrcChooser();return true;}
        if(hit(mouseX,mouseY,575,530,650,16)){double fraction=Math.max(0,Math.min(1,(x-589)/616));music.seek((long)(music.track().durationMs()*fraction));return true;}
        return super.mouseClicked(mouseX,mouseY,button);
    }
    private void musicMessage(String result){MinecraftClient.getInstance().execute(()->{});}
    private void openLrcChooser(){Thread thread=new Thread(()->{try{FileDialog dialog=new FileDialog((Frame)null,"Import synchronized lyrics",FileDialog.LOAD);dialog.setFile("*.lrc");dialog.setVisible(true);String file=dialog.getFile();if(file!=null)music.importLrc(java.nio.file.Path.of(dialog.getDirectory(),file));}catch(Exception ignored){}} ,"Stellar-LRC-chooser");thread.setDaemon(true);thread.start();}
    @Override public boolean charTyped(char chr,int modifiers){if(clientIdFocused&&chr>=32){clientIdInput+=chr;return true;}if(searchFocused&&chr>=32){searchInput+=chr;return true;}return super.charTyped(chr,modifiers);}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){
        if(keyCode==GLFW.GLFW_KEY_BACKSPACE){if(clientIdFocused&&!clientIdInput.isEmpty())clientIdInput=clientIdInput.substring(0,clientIdInput.length()-1);else if(searchFocused&&!searchInput.isEmpty())searchInput=searchInput.substring(0,searchInput.length()-1);return true;}
        if(keyCode==GLFW.GLFW_KEY_ENTER&&searchFocused){music.search(searchInput,result->musicMessage(result));return true;}
        if(keyCode==GLFW.GLFW_KEY_ESCAPE){close();return true;}return super.keyPressed(keyCode,scanCode,modifiers);
    }
    @Override public boolean shouldPause(){return false;}
}
