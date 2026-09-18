package com.example.custompictureframes.client.gui;

import com.example.custompictureframes.client.ClientNetworking;
import com.example.custompictureframes.client.image.*;
import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.model.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.texture.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.concurrent.*;

public final class PaintingEditorScreen extends Screen {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "painting-editor"); t.setDaemon(true); return t; });
    private LocalImageLoader.Loaded source;
    private int blocksW = 1, blocksH = 1;
    private double zoom = 1, panX, panY;
    private boolean stretch, loading, processing, dragging, closed;
    private int revision, renderedRevision = -1;
    private long changedAt;
    private Identifier preview;
    private int texW, texH, viewX, viewY, viewW, viewH;
    private ButtonWidget generate, choose, ratio, rotate;
    private SliderWidget zoomSlider;
    private TextFieldWidget nameField;
    private String paintingName = "";
    private Text status = Text.translatable("painting.editor.hint");
    public PaintingEditorScreen() { super(Text.translatable("painting.editor.title")); }
    private PaintingSpec spec() { return new PaintingSpec(blocksW, blocksH, zoom, panX, panY, stretch); }
    private void changed() { revision++; changedAt = System.currentTimeMillis(); }
    private ButtonWidget button(Text text, int x, int y, int width, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(text, action).dimensions(x, y, width, 20).build());
    }
    @Override protected void init() {
        closed = false;
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, 180, 52, Math.max(64, width-196), 20, Text.translatable("painting.name")));
        nameField.setMaxLength(PaintingNames.MAX_LENGTH);
        nameField.setText(paintingName);
        nameField.setChangedListener(text -> paintingName = text);
        nameField.setPlaceholder(Text.translatable("painting.name.placeholder"));
        choose = button(Text.translatable("painting.select"), 16, 40, 150, b -> select());
        button(Text.literal("−"), 110, 68, 24, b -> { blocksW = Math.max(1, blocksW-1); changed(); });
        button(Text.literal("+"), 140, 68, 26, b -> { blocksW = Math.min(Limits.MAX_BLOCKS, blocksW+1); changed(); });
        button(Text.literal("−"), 110, 96, 24, b -> { blocksH = Math.max(1, blocksH-1); changed(); });
        button(Text.literal("+"), 140, 96, 26, b -> { blocksH = Math.min(Limits.MAX_BLOCKS, blocksH+1); changed(); });
        zoomSlider = addDrawableChild(new SliderWidget(16, 124, 150, 20, Text.empty(), (zoom-1)/7) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Text.literal(String.format(java.util.Locale.ROOT, "Zoom %.2f×", zoom))); }
            @Override protected void applyValue() { zoom = 1+value*7; updateMessage(); changed(); }
        });
        ratio = button(modeLabel(), 16, 152, 150, b -> { stretch = !stretch; b.setMessage(modeLabel()); changed(); });
        button(Text.translatable("painting.reset"), 16, 180, 114, b -> { zoom=1; panX=panY=0; stretch=false; changed(); clearAndInit(); });
        rotate = button(Text.literal("90°"), 134, 180, 32, b -> rotate());
        rotate.setTooltip(Tooltip.of(Text.translatable("painting.rotate")));
        button(Text.translatable("gui.cancel"), 16, height-28, 80, b -> close());
        generate = button(Text.translatable("painting.generate"), Math.max(180, width-176), height-28, 160, b -> generate());
        updateButtons();
    }
    private Text modeLabel() { return Text.translatable(stretch ? "painting.stretch" : "painting.preserve"); }
    private void updateButtons() {
        if (generate != null) generate.active = source != null && !loading && !ClientNetworking.busy();
        if (choose != null) choose.active = !loading && !ClientNetworking.busy();
        if (rotate != null) rotate.active = source != null && !loading && !ClientNetworking.busy();
    }
    private void rotate() {
        if (source == null || loading || ClientNetworking.busy()) return;
        var input = source;
        loading = true; dragging = false; changed(); updateButtons();
        status = Text.translatable("painting.rotating");
        WORKER.execute(() -> {
            try {
                var rotated = LocalImageLoader.rotateClockwise(input);
                client.execute(() -> {
                    if (closed) return;
                    source = rotated; loading = false; panX = panY = 0; changed();
                    status = Text.literal(rotated.name() + " · " + rotated.image().getWidth() + "×" + rotated.image().getHeight());
                    updateButtons();
                });
            } catch (Exception | OutOfMemoryError e) {
                client.execute(() -> {
                    if (closed) return;
                    loading = false; status = Text.translatable("painting.rotate_error"); updateButtons();
                });
            }
        });
    }
    private void select() {
        loading = true; updateButtons(); status = Text.translatable("painting.selecting");
        WORKER.execute(() -> {
            try {
                var path = LocalFilePicker.choose();
                var loaded = path == null ? null : LocalImageLoader.load(path);
                client.execute(() -> {
                    if (closed) return;
                    loading = false;
                    if (loaded != null) {
                        source = loaded; zoom=1; panX=panY=0; changed();
                        status = Text.literal(loaded.name() + " · " + loaded.image().getWidth() + "×" + loaded.image().getHeight());
                        clearAndInit();
                    } else status = Text.translatable("painting.editor.hint");
                    updateButtons();
                });
            } catch (Exception e) {
                client.execute(() -> { if (!closed) { loading=false; status=Text.literal(e.getMessage() == null ? "Cannot open image" : e.getMessage()); updateButtons(); } });
            }
        });
    }
    private void generate() {
        if (source == null || loading || ClientNetworking.busy()) return;
        status = Text.translatable("painting.sending");
        ClientNetworking.generate(source.png(), spec(), paintingName, error -> {
            if (!closed) { status = error.isEmpty() ? Text.translatable("painting.created") : Text.literal(error); updateButtons(); }
        });
        updateButtons();
    }
    @Override public void tick() {
        updateButtons();
        if (closed || source == null || loading || processing || renderedRevision == revision || System.currentTimeMillis()-changedAt < 70) return;
        processing = true; int token = revision; var input = source.image(); var settings = spec();
        WORKER.execute(() -> {
            try {
                var result = ImagePipeline.render(input, settings, 768);
                NativeImage image = NativeImages.fromBuffered(result);
                client.execute(() -> {
                    processing = false;
                    if (closed || token != revision) { image.close(); return; }
                    if (preview != null) client.getTextureManager().destroyTexture(preview);
                    texW = image.getWidth(); texH = image.getHeight();
                    var texture = new NativeImageBackedTexture(image);
                    preview = client.getTextureManager().registerDynamicTexture("painting_editor", texture);
                    texture.setFilter(true, false); renderedRevision=token;
                });
            } catch (Exception | OutOfMemoryError e) {
                client.execute(() -> { processing=false; renderedRevision=token; if (!closed) status=Text.literal("Preview: " + e.getMessage()); });
            }
        });
    }
    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xf518202a);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width/2, 14, 0xfff3e5c0);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("painting.width", blocksW), 16, 74, 0xffffffff);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("painting.height", blocksH), 16, 102, 0xffffffff);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("painting.name"), 180, 40, 0xffffffff);
        int px=180, py=80, pw=Math.max(64, width-196), ph=Math.max(32, height-146);
        ctx.fill(px, py, px+pw, py+ph, 0xff4b5157);
        for(int y=py; y<py+ph; y+=20) {
            ctx.fill(px, y, px+pw, y+1, 0xff333a42);
            for(int x=px+((y-py)/20%2)*20; x<px+pw; x+=40) ctx.fill(x,y,x+1,Math.min(y+20,py+ph),0xff333a42);
        }
        double scale = Math.min((double)(pw-16)/blocksW, (double)(ph-16)/blocksH);
        viewW=Math.max(1,(int)(blocksW*scale)); viewH=Math.max(1,(int)(blocksH*scale));
        viewX=px+(pw-viewW)/2; viewY=py+(ph-viewH)/2;
        ctx.fill(viewX-3,viewY-3,viewX+viewW+3,viewY+viewH+3,0xff9b7348);
        ctx.fill(viewX,viewY,viewX+viewW,viewY+viewH,0xff242424);
        if(preview!=null) ctx.drawTexture(preview,viewX,viewY,viewW,viewH,0,0,texW,texH,texW,texH);
        for(int x=1; x<blocksW; x++) { int xx=viewX+x*viewW/blocksW; ctx.fill(xx,viewY,xx+1,viewY+viewH,0x55ffffff); }
        for(int y=1; y<blocksH; y++) { int yy=viewY+y*viewH/blocksH; ctx.fill(viewX,yy,viewX+viewW,yy+1,0x55ffffff); }
        ctx.drawCenteredTextWithShadow(textRenderer, blocksW+" × "+blocksH+" = "+(blocksW*blocksH)+" blocks", px+pw/2, py+ph+5, 0xffffffff);
        Text line = ClientNetworking.busy() ? Text.translatable("painting.sending").copy().append(" "+ClientNetworking.status) : status;
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line.getString(), width-32), 16, height-44, 0xffb9d3dd);
        super.render(ctx,mouseX,mouseY,delta);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0 && source!=null && x>=viewX && x<viewX+viewW && y>=viewY && y<viewY+viewH) { dragging=true; return true; }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(dragging && source!=null && button==0) {
            var crop=ImagePipeline.crop(source.image().getWidth(),source.image().getHeight(),spec());
            double extraX=source.image().getWidth()-crop.width(), extraY=source.image().getHeight()-crop.height();
            if(extraX>0.0001) panX=Math.clamp(panX-2*dx*crop.width()/viewW/extraX,-1,1);
            if(extraY>0.0001) panY=Math.clamp(panY-2*dy*crop.height()/viewH/extraY,-1,1);
            changed(); return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) { dragging=false; return super.mouseReleased(x,y,button); }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(x>=viewX && y>=viewY && x<viewX+viewW && y<viewY+viewH) {
            zoom=Math.clamp(zoom+vertical*.15,1,8); changed(); clearAndInit(); return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void removed() {
        closed=true; revision++;
        if(preview!=null) { client.getTextureManager().destroyTexture(preview); preview=null; }
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // This editor paints its own opaque background before its preview and controls.
    }
}
