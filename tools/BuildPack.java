import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Deterministic integer-pixel GUI renderer. No TTF, interpolation or antialiasing. */
public class BuildPack {
    static final Path ROOT=Path.of("resourcepack");
    static final Map<Character,String[]> FONT=new HashMap<>();
    static final List<String> GUI=new ArrayList<>(), HUD=new ArrayList<>();
    static final int GOLD=0xffffbc43, WHITE=0xffffefbb, ORANGE=0xffff680d, DARK=0xff18171a;
    static void glyph(char c,String s){FONT.put(c,s.split(" "));}
    static void fonts(){
        String[] letters={"А:010 101 111 101 101","Б:111 100 110 101 110","В:110 101 110 101 110","Г:111 100 100 100 100","Д:0110 0110 0110 1111 1001","Е:111 100 110 100 111","Ж:10101 01110 00100 01110 10101","З:110 001 010 001 110","И:10001 10011 10101 11001 10001","Й:01010 00100 10001 10011 10101 11001 10001","К:101 101 110 101 101","Л:011 101 101 101 101","М:10001 11011 10101 10001 10001","Н:101 101 111 101 101","О:010 101 101 101 010","П:111 101 101 101 101","Р:110 101 110 100 100","С:011 100 100 100 011","Т:111 010 010 010 010","У:101 101 011 001 110","Ф:010 111 111 010 010","Х:101 101 010 101 101","Ц:1010 1010 1010 1110 0001","Ч:101 101 111 001 001","Ш:10101 10101 10101 10101 11111","Щ:101010 101010 101010 111110 000001","Ъ:1100 0100 0110 0101 0110","Ы:10001 10001 11001 10101 11001","Ь:100 100 110 101 110","Э:110 001 011 001 110","Ю:1010 1101 1101 1101 1010","Я:011 101 011 101 101","0:111 101 101 101 111","1:010 110 010 010 111","2:110 001 010 100 111","3:110 001 010 001 110","4:101 101 111 001 001","5:111 100 110 001 110","6:011 100 111 101 111","7:111 001 010 010 010","8:111 101 111 101 111","9:111 101 111 001 110","°:110 110 000 000 000","C:011 100 100 100 011","-:000 000 111 000 000",".:0 0 0 0 1","I:1 1 1 1 1","/:001 001 010 100 100","X:101 101 010 101 101"};
        for(String l:letters)glyph(l.charAt(0),l.substring(2));
        glyph('Ё',"101 000 111 100 110 100 111");
        glyph(':',"0 1 0 1 0");glyph('+',"000 010 111 010 000");glyph('≈',"01010 10100 00000 01010 10100");
    }
    static BufferedImage image(int w,int h){return new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);}
    static void rect(BufferedImage b,int x,int y,int w,int h,int c){for(int yy=Math.max(0,y);yy<Math.min(b.getHeight(),y+h);yy++)for(int xx=Math.max(0,x);xx<Math.min(b.getWidth(),x+w);xx++)b.setRGB(xx,yy,c);}
    static void frame(BufferedImage b,int x,int y,int w,int h,int edge){rect(b,x,y,w,h,0xff060609);rect(b,x+1,y+1,w-2,h-2,edge);rect(b,x+2,y+2,w-4,h-4,DARK);rect(b,x+2,y+2,w-4,1,0xff69412a);}
    static int width(String s,int scale){int n=0;for(char c:s.toCharArray())n+=(c==' '?3:FONT.getOrDefault(c,FONT.get('-'))[0].length()+1)*scale;return Math.max(0,n-scale);}
    static void text(BufferedImage b,String s,int x,int y,int color,int scale){for(char c:s.toCharArray()){if(c==' '){x+=3*scale;continue;}String[] rows=FONT.getOrDefault(c,FONT.get('-'));int top=y-Math.max(0,rows.length-5)*scale;for(int yy=0;yy<rows.length;yy++)for(int xx=0;xx<rows[yy].length();xx++)if(rows[yy].charAt(xx)=='1')rect(b,x+xx*scale,top+yy*scale,scale,scale,color);x+=(rows[0].length()+1)*scale;}}
    static void centered(BufferedImage b,String s,int center,int y,int color,int scale){text(b,s,center-width(s,scale)/2,y,color,scale);}
    static void save(BufferedImage b,String file,int cp,boolean hud) throws Exception{
        // Fixed rightmost opaque marker makes bitmap advances deterministic (width + 1).
        b.setRGB(b.getWidth()-1,b.getHeight()-1,0x01000000);
        Path path=ROOT.resolve("assets/servermine/textures/"+file);Files.createDirectories(path.getParent());ImageIO.write(b,"PNG",path.toFile());
        String provider="{\"type\":\"bitmap\",\"file\":\"servermine:"+file+"\",\"ascent\":"+(hud?32:13)+",\"height\":"+b.getHeight()+",\"chars\":[\"\\u"+String.format("%04x",cp)+"\"]}";
        (hud?HUD:GUI).add(provider);
    }
    static BufferedImage layer(){return image(256,222);}
    static void gui() throws Exception{
        BufferedImage base=layer();
        // Metal casing with integer-pixel hammered texture and orange seams.
        Random random=new Random(37);rect(base,25,0,205,222,0xff080809);frame(base,30,0,180,222,0xff74685e);
        for(int y=3;y<219;y++)for(int x=33;x<207;x++){int noise=random.nextInt(5);base.setRGB(x,y,0xff000000|((21+noise)<<16)|((20+noise)<<8)|(22+noise));}
        for(int y=3;y<220;y+=14){frame(base,25,y,7,10,0xff73675a);rect(base,28,y+3,2,2,GOLD);frame(base,209,y,7,10,0xff73675a);rect(base,211,y+3,2,2,GOLD);}
        frame(base,38,2,164,19,0xffaa5d25);rect(base,43,18,154,1,ORANGE);
        for(int x=40;x<201;x+=32){rect(base,x,3,2,2,GOLD);rect(base,x,19,2,1,ORANGE);}
        // Narrow crimson banners flank the title without changing any slot hitbox.
        for(int x:new int[]{26,210}){rect(base,x,3,5,21,0xff7d1110);rect(base,x,3,5,1,GOLD);rect(base,x+1,20,3,5,0xffbc2814);}
        // ImageGen supplies decorative metal only. Slot geometry remains fully code-defined.
        Path artFile=Path.of("tools/forge-art.png");
        if(Files.exists(artFile)){
            BufferedImage art=ImageIO.read(artFile.toFile());
            for(int y=0;y<222;y++)for(int x=0;x<256;x++)base.setRGB(x,y,art.getRGB(x*art.getWidth()/256,y*art.getHeight()/222));
            // Remove every generative slot/number/gauge from interactive regions.
            for(int y=22;y<217;y++)for(int x=37;x<221;x++){
                int sx=art.getWidth()*66/100+(x%12)*2,sy=art.getHeight()*29/100+(y%12)*2;
                int pixel=art.getRGB(sx,sy);int r=((pixel>>16)&255)/2,g=((pixel>>8)&255)/2,b=(pixel&255)/2;
                base.setRGB(x,y,0xff000000|(r<<16)|(g<<8)|b);
            }
            // Keep one continuous right rim. The gauge belongs inside the opaque
            // panel; clearing its old area used to cut the rim in half.
            rect(base,221,22,35,200,0);
            rect(base,233,0,23,22,0);
            for(int y=217;y<222;y++)for(int x=207;x<221;x++)base.setRGB(x,y,base.getRGB(x-110,y));
            for(int y=22;y<222;y++)for(int x=221;x<233;x++)base.setRGB(x,y,base.getRGB(257-x,y));
        }
        // Cover the vanilla chest's top edge, including transparent art pixels.
        rect(base,32,0,176,1,0xff161515);
        for(int x:new int[]{58,94,130}){rect(base,x-2,34,20,20,0xff402515);rect(base,x-1,35,18,18,x==94?0xff9bafbd:ORANGE);rect(base,x,36,16,16,0xff0b0b0c);}
        for(int slot=28;slot<=32;slot++)slot(base,slot);
        for(int slot=38;slot<=41;slot++)slot(base,slot);
        frame(base,206,30,14,82,0xffae6a24);rect(base,210,34,6,74,0xff31160c);
        frame(base,160,35,42,18,0xff694126);
        frame(base,151,73,53,21,0xff995623);
        frame(base,39,112,139,13,0xff995623);
        frame(base,183,107,19,19,0xffff5b16);rect(base,185,109,15,15,0xffa50d10);
        text(base,"X",190,112,GOLD,2);
        rect(base,34,126,172,1,0xff814d2c);
        // Minecraft draws the localized inventory label after the title glyphs.
        // Leave it as the only label, with a light metal plate for its dark text.
        rect(base,38,127,166,11,0xff6b4b30);
        rect(base,39,128,164,9,0xffc2ac88);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)frame(base,39+col*18,139+row*18,18,18,0xff9d6637);
        for(int col=0;col<9;col++)frame(base,39+col*18,197,18,18,0xffb7783a);
        save(base,"gui/forge/forge_base.png",0xE001,false);
        BufferedImage labels=layer();centered(labels,"КУЗНЕЧНЫЙ ГОРН",120,9,GOLD,2);
        centered(labels,"ТОПЛИВО",65,25,WHITE,1);centered(labels,"МЕТАЛЛ",101,25,WHITE,1);centered(labels,"НАГРЕВ",137,25,WHITE,1);centered(labels,"ТЕМП.",181,25,WHITE,1);
        centered(labels,"ИНСТРУМЕНТЫ",101,65,GOLD,1);text(labels,"БРОНЯ",151,95,GOLD,1);
        centered(labels,"СОСТОЯНИЕ ГОРНА",109,107,GOLD,1);
        text(labels,"УГОЛЬ",154,75,GOLD,1);text(labels,"≈",175,75,GOLD,1);
        text(labels,"НА",154,85,GOLD,1);text(labels,":",172,85,WHITE,1);text(labels,":",184,85,WHITE,1);
        save(labels,"gui/forge/forge_labels.png",0xE002,false);
        for(int n=0;n<=20;n++){BufferedImage heat=layer();int h=(int)Math.round(72*n/20.0);for(int y=0;y<h;y++)rect(heat,211,106-y,4,1,y>48?GOLD:y>25?0xffff9d0a:0xffff5b05);int marker=106-(int)Math.round(72*(900-20.0)/(1200-20));rect(heat,207,marker,13,1,WHITE);save(heat,"gui/forge/heat/forge_heat_"+String.format("%02d",n)+".png",0xE020+n,false);}
        for(int pos=0;pos<4;pos++)for(int digit=0;digit<10;digit++){BufferedImage d=layer();text(d,Integer.toString(digit),165+pos*4,41,WHITE,1);save(d,"gui/forge/digits/p"+pos+"_"+digit+".png",0xE100+pos*16+digit,false);}
        BufferedImage unit=layer();text(unit,"°C",182,41,WHITE,1);save(unit,"gui/forge/digits/unit_c.png",0xE140,false);
        int[] clockX={164,168,176,180,188,192};
        for(int pos=0;pos<6;pos++)for(int digit=0;digit<10;digit++){BufferedImage d=layer();text(d,Integer.toString(digit),clockX[pos],85,WHITE,1);save(d,"gui/forge/fuel/time_"+pos+"_"+digit+".png",0xE200+pos*16+digit,false);}
        String fuelChars="0123456789+-";
        for(int pos=0;pos<4;pos++)for(int digit=0;digit<fuelChars.length();digit++){BufferedImage d=layer();text(d,String.valueOf(fuelChars.charAt(digit)),184+pos*4,75,WHITE,1);save(d,"gui/forge/fuel/coal_"+pos+"_"+digit+".png",0xE300+pos*16+digit,false);}
        BufferedImage overflow=layer();text(overflow,"+",198,85,WHITE,1);save(overflow,"gui/forge/fuel/time_overflow.png",0xE340,false);
        String[] statuses={"ХОЛОДНЫЙ","НАГРЕВАЕТСЯ","ГОТОВ К РАБОТЕ","ОСТЫВАЕТ","НЕТ ТОПЛИВА","НЕДОСТАТОЧНО МЕТАЛЛА","НЕПОДХОДЯЩИЙ МЕТАЛЛ","ЗАГОТОВКА СОЗДАНА","ПОВТОРНЫЙ НАГРЕВ","ЗАГОТОВКА НАГРЕТА","НЕВЕРНАЯ ЗАГОТОВКА","АКТИВНАЯ СЕССИЯ КОВКИ","ГОРН ПОВРЕЖДЁН"};
        for(int n=0;n<statuses.length;n++){BufferedImage status=layer();centered(status,statuses[n],109,117,n==2?0xffc8f394:WHITE,1);save(status,"gui/forge/status/"+n+".png",0xE050+n,false);}
        GUI.add("{\"type\":\"space\",\"advances\":{\"\\ue700\":-40,\"\\ue701\":-257}}");
        write("assets/servermine/font/forge.json","{\"providers\":["+String.join(",",GUI)+"]}");
        write("assets/minecraft/font/default.json","{\"providers\":[{\"type\":\"reference\",\"id\":\"servermine:forge\"}]}");
        write("assets/servermine/items/empty.json","{\"model\":{\"type\":\"minecraft:empty\"}}");
        BufferedImage preview=image(256,222);var g=preview.createGraphics();g.drawImage(base,0,0,null);g.drawImage(labels,0,0,null);g.drawImage(ImageIO.read(ROOT.resolve("assets/servermine/textures/gui/forge/heat/forge_heat_15.png").toFile()),0,0,null);
        String sampleTime="002400",sampleCoal="  18";
        for(int pos=0;pos<6;pos++)g.drawImage(ImageIO.read(ROOT.resolve("assets/servermine/textures/gui/forge/fuel/time_"+pos+"_"+sampleTime.charAt(pos)+".png").toFile()),0,0,null);
        for(int pos=0;pos<4;pos++)if(sampleCoal.charAt(pos)!=' ')g.drawImage(ImageIO.read(ROOT.resolve("assets/servermine/textures/gui/forge/fuel/coal_"+pos+"_"+sampleCoal.charAt(pos)+".png").toFile()),0,0,null);
        g.dispose();
        ImageIO.write(preview,"PNG",Path.of("gui-preview.png").toFile());
        BufferedImage zoom=image(1024,888);for(int y=0;y<888;y++)for(int x=0;x<1024;x++)zoom.setRGB(x,y,preview.getRGB(x/4,y/4));ImageIO.write(zoom,"PNG",Path.of("gui-preview-4x.png").toFile());
    }
    static void slot(BufferedImage b,int slot){int x=32+8+slot%9*18,y=18+slot/9*18;rect(b,x-1,y-1,18,18,0xffa66b37);rect(b,x,y,16,16,0xff0b0b0c);rect(b,x,y,16,1,0xff4d2f1c);}
    static BufferedImage hud(){return image(192,40);}
    /** Native item models sample original vanilla texels. No replacement artwork. */
    static boolean[][] workpieceMask(BufferedImage source,String product,boolean armor){
        boolean[][] mask=new boolean[16][16];
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int pixel=source.getRGB(x,y),r=pixel>>16&255,g=pixel>>8&255,b=pixel&255;
            boolean wood=r>b+12&&r>g+5;
            mask[y][x]=(pixel>>>24)>0&&(armor||(!wood&&(!product.equals("hoe")||y<=6)));
        }
        if(!armor){
            // Remove detached sword pommel and any isolated grip pixels.
            boolean[][] visited=new boolean[16][16];List<int[]> largest=List.of();
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(mask[y][x]&&!visited[y][x]){
                var component=new ArrayList<int[]>();var queue=new ArrayDeque<int[]>();queue.add(new int[]{x,y});visited[y][x]=true;
                while(!queue.isEmpty()){int[] p=queue.remove();component.add(p);for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int nx=p[0]+d[0],ny=p[1]+d[1];if(nx>=0&&nx<16&&ny>=0&&ny<16&&mask[ny][nx]&&!visited[ny][nx]){visited[ny][nx]=true;queue.add(new int[]{nx,ny});}}}
                if(component.size()>largest.size())largest=component;
            }
            mask=new boolean[16][16];for(int[] p:largest)mask[p[1]][p[0]]=true;
        }
        return mask;
    }
    static int[] maskOffset(boolean[][] mask,boolean armor){
        if(armor)return new int[]{0,0};
        int minX=16,maxX=-1,minY=16,maxY=-1;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(mask[y][x]){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        if(maxX<0)throw new IllegalStateException("Empty workpiece model");
        return new int[]{(16-(maxX-minX+1))/2-minX,(16-(maxY-minY+1))/2-minY};
    }
    static String face(int u0,int v0,int u1,int v1){return "{\"uv\":["+u0+","+v0+","+u1+","+v1+"],\"texture\":\"#layer0\",\"tintindex\":0}";}
    static String maskedGeometry(boolean[][] mask,int[] offset){
        var elements=new ArrayList<String>();
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(mask[y][x]){
            int left=x;while(x+1<16&&mask[y][x+1])x++;int right=x+1;
            int lower=15-y-offset[1];
            elements.add("{\"from\":["+(left+offset[0])+","+lower+",7.5],\"to\":["+(right+offset[0])+","+(lower+1)+",8.5],\"shade\":false,\"faces\":{"
                +"\"south\":"+face(left,y,right,y+1)+",\"north\":"+face(right,y,left,y+1)
                +",\"east\":"+face(right-1,y,right,y+1)+",\"west\":"+face(left,y,left+1,y+1)
                +",\"up\":"+face(left,y,right,y+1)+",\"down\":"+face(left,y,right,y+1)+"}}");
        }
        return "["+String.join(",",elements)+"]";
    }
    static void itemModels()throws Exception{
        String[] products={"sword","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots"};
        String[] names={"МЕЧ","КИРКА","ТОПОР","ЛОПАТА","МОТЫГА","ШЛЕМ","НАГРУДНИК","ПОНОЖИ","БОТИНКИ"};
        String[] heat={"cold","warm","hot"};int[] colors={0xffffff,0xd45c3f,0xffb347};
        Path vanilla=Path.of("tools/vanilla-items");
        String generated=Files.readString(vanilla.resolve("generated.json"));
        // Copy vanilla item display transforms, excluding its generated-geometry marker.
        write("assets/servermine/models/item/forge/workpiece/base.json","{\"gui_light\":\"front\","+generated.substring(generated.indexOf("\"display\"")));
        BufferedImage preview=image(1092,948);rect(preview,0,0,1092,948,0xff202127);
        text(preview,"ЗАГОТОВКИ 16 X 16",16,12,GOLD,2);
        text(preview,"ЖЕЛЕЗО",220,35,WHITE,2);text(preview,"ЗОЛОТО",550,35,GOLD,2);text(preview,"МЕДЬ",874,35,0xffdc9263,2);
        String[] captions={"ХОЛОДНАЯ","ТЁПЛАЯ","ГОРЯЧАЯ"};
        for(int col=0;col<9;col++)centered(preview,captions[col%3],156+col*108,58,WHITE,2);
        for(int p=0;p<products.length;p++){
            boolean armor=p>=5;String product=products[p];
            BufferedImage iron=ImageIO.read(vanilla.resolve("iron_"+product+".png").toFile());
            if(iron.getWidth()!=16||iron.getHeight()!=16)throw new IllegalStateException("Expected vanilla 16x16 texture");
            boolean[][] mask=workpieceMask(iron,product,armor);int[] offset=maskOffset(mask,armor);
            text(preview,names[p],8,115+p*96,WHITE,2);
            for(int m=0;m<3;m++){
                String metal=new String[]{"iron","gold","copper"}[m],vanillaMetal=new String[]{"iron","golden","copper"}[m];
                String root="forge/workpiece/"+metal+"/"+product;
                String texture="minecraft:item/"+vanillaMetal+"_"+product;
                String parent=armor?"minecraft:item/generated":"servermine:item/forge/workpiece/base";
                write("assets/servermine/models/item/"+root+".json","{\"parent\":\""+parent+"\",\"textures\":{\"layer0\":\""+texture+"\",\"particle\":\""+texture+"\"}"+(armor?"":",\"elements\":"+maskedGeometry(mask,offset))+"}");
                BufferedImage source=ImageIO.read(vanilla.resolve(vanillaMetal+"_"+product+".png").toFile());
                for(int h=0;h<3;h++){
                    write("assets/servermine/items/"+root+"/"+heat[h]+".json","{\"hand_animation_on_swap\":false,\"model\":{\"type\":\"minecraft:model\",\"model\":\"servermine:item/"+root+"\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":"+colors[h]+"}]}}");
                    // Orthographic GUI preview of the model's sampled faces and tint.
                    int left=124+(m*3+h)*108,top=80+p*96;
                    rect(preview,left-4,top-4,72,72,0xff101114);
                    for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(mask[y][x]){
                        int pixel=source.getRGB(x,y),tint=colors[h];
                        int r=(pixel>>16&255)*(tint>>16&255)/255,g=(pixel>>8&255)*(tint>>8&255)/255,b=(pixel&255)*(tint&255)/255;
                        rect(preview,left+(x+offset[0])*4,top+(y+offset[1])*4,4,4,(pixel&0xff000000)|(r<<16)|(g<<8)|b);
                    }
                }
            }
        }
        ImageIO.write(preview,"PNG",Path.of("workpiece-models-preview.png").toFile());
        System.out.println("Built 81 workpiece item definitions from original vanilla texels; finished items unchanged.");
    }
    static void hammerSprite() throws Exception {
        Path source=Path.of("tools/hammer-16.png");
        BufferedImage sprite=ImageIO.read(source.toFile());
        if(sprite.getWidth()!=16||sprite.getHeight()!=16||!sprite.getColorModel().hasAlpha())throw new IllegalStateException("Hammer must be a transparent 16x16 sprite");
        Path target=ROOT.resolve("assets/servermine/textures/item/forge/hammer.png");
        Files.createDirectories(target.getParent());Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
        write("assets/servermine/models/item/forge/hammer.json","{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"servermine:item/forge/hammer\"}}");
        write("assets/servermine/items/forge/hammer.json","{\"hand_animation_on_swap\":false,\"model\":{\"type\":\"minecraft:model\",\"model\":\"servermine:item/forge/hammer\"}}");
    }
    static void hudPack() throws Exception{
        BufferedImage base=hud();frame(base,0,12,192,28,0xff9c612b);text(base,"КОВКА",5,16,GOLD,1);frame(base,5,25,136,9,0xff6a6d66);rect(base,7,27,132,5,0xff242927);save(base,"hud/base.png",0xE000,true);
        for(int n=0;n<3;n++){BufferedImage b=hud();text(b,"I".repeat(n+1),42,16,WHITE,1);save(b,"hud/stage"+n+".png",0xE001+n,true);}
        for(int n=0;n<31;n++){BufferedImage b=hud();rect(b,7+(int)Math.round(n/30.0*130),26,1,7,0xffffffff);save(b,"hud/slider"+n+".png",0xE010+n,true);}
        for(int width=0;width<29;width++)for(int n=0;n<31;n++){BufferedImage b=hud();double w=.04+width*.02;int pixels=(int)Math.round(w*132);int x=Math.clamp(7+(int)Math.round(n/30.0*132)-pixels/2,7,139-pixels);rect(b,x,27,pixels,5,0xff58bf4f);save(b,"hud/zones/w"+width+"_p"+n+".png",0xE200+width*31+n,true);}
        for(int n=0;n<=10;n++){BufferedImage b=hud();frame(b,148,25,38,7,0xff814221);rect(b,150,27,(int)Math.round(n*3.4),3,ORANGE);save(b,"hud/temp"+n+".png",0xE040+n,true);}
        for(int n=0;n<4;n++){BufferedImage b=hud();for(int q=0;q<3;q++){int x=150+q*12;rect(b,x+2,16,3,1,q<n?GOLD:0xff4e4843);rect(b,x+1,17,5,2,q<n?GOLD:0xff4e4843);rect(b,x+2,19,3,1,q<n?GOLD:0xff4e4843);}save(b,"hud/quality"+n+".png",0xE050+n,true);}
        String[] status={"","УДАР","ОСТЫЛО","ЛИМИТ","ГОТОВО"};for(int n=0;n<status.length;n++){BufferedImage b=hud();text(b,status[n],100,16,GOLD,1);save(b,"hud/status"+n+".png",0xE060+n,true);}
        String[] popups={"ХОРОШАЯ КОВКА","ОТЛИЧНАЯ КОВКА","МАСТЕРСКАЯ КОВКА","ПРОМАХ","МЕТАЛЛ ОСТЫЛ","ЛИМИТ КАЧЕСТВА"};for(int n=0;n<6;n++){BufferedImage b=hud();frame(b,36,0,120,11,0xff89532a);centered(b,popups[n],96,3,GOLD,1);save(b,"hud/popup"+n+".png",0xE070+n,true);}
        HUD.add("{\"type\":\"space\",\"advances\":{\"\\ue7f0\":-193,\"\\ue7f1\":193}}");write("assets/servermine/font/forge_hud.json","{\"providers\":["+String.join(",",HUD)+"]}");
    }
    static void write(String path,String content)throws Exception{Path out=ROOT.resolve(path);Files.createDirectories(out.getParent());Files.writeString(out,content,StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{
        fonts();gui();hudPack();itemModels();hammerSprite();write("pack.mcmeta","{\"pack\":{\"description\":\"ServerMine ForgeSystem 3.1.0\",\"min_format\":[88,0],\"max_format\":[88,0]}}");
        try(var out=new ZipOutputStream(Files.newOutputStream(Path.of("src/main/resources/forge.zip")))){try(var walk=Files.walk(ROOT)){for(Path f:walk.filter(Files::isRegularFile).sorted().toList()){ZipEntry entry=new ZipEntry(ROOT.relativize(f).toString().replace('\\','/'));entry.setTime(0);out.putNextEntry(entry);Files.copy(f,out);out.closeEntry();}}}
        System.out.println("Built forge.zip: "+GUI.size()+" GUI providers, "+HUD.size()+" HUD providers");
    }
}
