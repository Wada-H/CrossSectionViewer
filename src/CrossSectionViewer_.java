import hw.crosssectionviewer.CreateCrossSectionImage;
import hw.crosssectionviewer.CreateCrossSectionViewerUI;
import hw.crosssectionviewer.OverlayManager;
import hw.crosssectionviewer.OverlaySM;
import ij.*;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.frame.PlugInFrame;
import ij.process.LUT;
import javafx.embed.swing.JFXPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;


/**
 *
 * 20180322 first version
 * 20180323 軽微な手直し
 * 20180326 overlayに登録されたROIを利用してstack画像を作る機能を考える。
 *  ->o.k
 *
 * 20180326 小椋氏指摘の問題(CellDetection)がこれにも存在する。
 *          具体的には登録済みROIと同じROIが複数登録されてしまう問題。
 *  ->登録しようとするROIの重複checkを導入することで解消
 *
 * 20180326 channelが1のときstack画像作成時にComposite modeに関連したエラーがでる。
 *  ->o.k
 *
 * 20180327 何かの拍子にROIを描かない状態でclickした場合、overlayに次のROIが登録されない不具合
 *  ->LineRoiを判定している中に登録処理を入れることで解消
 *
 * 20180327 複数channelの場合処理が引っかかるため、channel毎の並列に変更(以前は逐次)
 *
 * 20180403 channel数が増えると動作が厳しくなるので、さらにcreateImageをthread化
 *
 * 20180406 LineWidthが2以上の時の画像のブレ(Straighten.rotateLine()のバグ)を回避、また平均値から最大値を取るように変更
 * 20180406 CompositeIamge時、Roiの長さを変えていると、CrossSectionImageをCompositeImageに代入する際にエラーでる
 *  ->0409 delayを設けることで短くする時は回避出来ていそうだが、長くする時は依然として残る。色々試すも解決に至らず。並列処理を行うに辺り、ImagePlusへの代入時の配列長のエラーである。
 *  ->0409とりあえず、保留。
 *
 * 20180409 Roiを各C, Tの連動があるといいかも。また、この機能は任意に切り替えられるとなお良し。
 *  ->mouseReleased内で記述か？
 *
 * 20180410 Roiの連動機能(T)を追加。これにともなって、chageActiveRoi()を追加。また、Roiが選択されていない場合の処理も追加。
 *
 * 20190227 Average projectionに対応。
 *  ->そもそもコードには機能があったので、UIの変更だけで終了。
 *  ついでに画像を作る際のt-axisの処理方法を変更
 *
 * 20190424 createXZimageの追加
 *  これによってy方向の全pixelに対してXZ画像が作成可能
 *
 * 20190709 Z->Yの拡張率を整数から小数へ変更
 *  これにより等倍表示の精度があがる
 *  また、Calibrationが正しくなるよう調整
 *
 * 20190917 segmented line, free line でも処理できるように改修
 *  segmented lineを引く際に、途中段階がうまく反映されないためその段階を無視することで回避
 *
 * 20190924 segmented lineなどでFloatPolygon化する際、getInterpolatedPolygon()に変更
 *
 * 20190930 vertical mode追加 (SheetMeshProjectionで使用)
 *
 * 20191209 createXZimageの修正(roundありなしが混在したためround有りに変更)
 * 20191209 CreateCrossSectionImage > resizeXのガウシアン処理の方向及び値の変更(gsdパラメーターの追加), ZTinterpolation参照。ただし、今後変更の可能性あり
 * 20210402 resize後のgaussian filterが効いていないことに気がつく。なぜ前回修正分で方向も変更したのか？（どこかのバージョンでfilterの仕様変更があったか？）
 * 20230208 どうやらgsd部分の数値が悪いようだ。以前のものに戻す。LucyRichardson法によるボケ除去を試みる。
 *  それらしく出来たが、出来上がりの画像のdepthを揃えたり、MinMaxの調整がまだできていない。
 * 20231106 バックグラウンドが0もしくは極小のとき、画像を作りきれない不具合がある。(高輝度であるべきところが抜ける)
 *          この影響はphoton counting で撮影した際にかなり影響が出るため早急に対策が必要
 *          -> おそらく0のときだけなので画像から1引いて、1足すことで対応する。
 *
 *
 */


public class CrossSectionViewer_ extends PlugInFrame implements ImageListener, MouseListener, MouseMotionListener, MouseWheelListener, WindowListener, KeyListener{

    static String version = "20230207";

    CreateCrossSectionViewerUI ui;

    CreateCrossSectionImage createCrossSectionImage;

    private ImagePlus mainImage;
    //private CompositeImage ci_imp;
    private CompositeImage crossSectionImage = null;
    private ImagePlus crossSectionImage_single = null; // for x-z image, single channel
    private ImageStack newStackImage = null;

    private ImageCanvas ic;
    private int chSize;
    private int zSize;
    private int tSize;
    private int imgDepth;

    private int imgWidth;
    private int imgHeight;

    // overlay //
    private OverlayManager overlayManager;

    // Panel //
    Point ij_location;


    // buffer value //
    private int oldCh;
    private LUT old_lut = null;
    private int oldT;
    private int selectedRoiIndex;

    private long orth_timer = 0;

    public CrossSectionViewer_() {
        super("CrossSectionViewer ver.1." + version);

        Roi.setColor(Color.YELLOW);
        if(checkImage()){
            this.getBasicInformation(mainImage);

            this.createPanelFx();

            this.setListener(mainImage);
            createCrossSectionImage = new CreateCrossSectionImage(mainImage);
            //if(mainImage.isComposite()) {
            //    ci_imp = (CompositeImage) mainImage;
            //}

        }else{
            IJ.noImage();
            return;
        }

    }


    public boolean checkImage(){
        boolean b = false;

        ImagePlus checkImage = WindowManager.getCurrentImage();
        if(checkImage == null){
            b = false;
        }else{
            if(checkImage.isHyperStack() == false){
                IJ.run(checkImage, "Stack to Hyperstack...", ""); //なんかgetWindow().toFront()でエラー出る
            }
            mainImage = WindowManager.getCurrentImage();
            ic = mainImage.getCanvas();
            overlayManager = new OverlayManager(mainImage);


            b = true;
        }
        return b;
    }

    public void getBasicInformation(ImagePlus img){
        chSize = img.getNChannels();
        zSize = img.getNSlices();
        tSize = img.getNFrames();
        imgWidth = img.getWidth();
        imgHeight = img.getHeight();
        imgDepth = img.getBitDepth();

        oldCh = img.getC();
        oldT = img.getT();
        if(mainImage.isComposite() == false){
            old_lut = mainImage.getProcessor().getLut();
        }


    }


    public void createPanelFx(){
        ui = new CreateCrossSectionViewerUI(mainImage, "ui.fxml");
        ui.setOverlayManager(overlayManager);
        JFXPanel jfxPanel = ui.getFXML();
        IJ.setTool(Toolbar.LINE);
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        //this.setBounds(100,150, jfxPanel.getWidth(), jfxPanel.getHeight()); //setPanelPositionしてる
        this.setSize((int)jfxPanel.getScene().getWidth(), (int)jfxPanel.getScene().getHeight());//なぜかScene経由でないと取得できない
        this.add(jfxPanel);
        //this.pack(); //推奨サイズのｗindow
        this.setPanelPosition();
        this.setVisible(true);//thisの表示
    }





    public void setPanelPosition(){
        ij_location = IJ.getInstance().getLocation(); //imagejのtoolboxの開始座標
        int ij_height = IJ.getInstance().getHeight();
        this.setLocation(ij_location.x, ij_location.y + ij_height);
    }



    public void setListener(ImagePlus img){

        ic.addMouseListener(this);

        ImagePlus.addImageListener(this);

        img.getWindow().addWindowListener(this);
        img.getWindow().addMouseListener(this);
        img.getWindow().addWindowListener(this);

        ic.addMouseMotionListener(this);
        ic.addMouseWheelListener(this);

    }

    public void removeListener() {
        ic.removeMouseListener(this);

        ImagePlus.removeImageListener(this);

        mainImage.getWindow().removeWindowListener(this);
        mainImage.getWindow().removeMouseListener(this);
        mainImage.getWindow().removeMouseMotionListener(this);

        ic.removeMouseMotionListener(this);
        ic.removeMouseWheelListener(this);
    }


    private ImagePlus createCrossSectionImage(){
        createCrossSectionImage.setMode(ui.getModeId());
        createCrossSectionImage.setT(mainImage.getT());
        ImagePlus csi = createCrossSectionImage.createCrossSectionImage();

        delay_time = (int)createCrossSectionImage.getCreatingTime();

        //if(csi.isComposite()){
        //    delay_time = delay_time + 10;
        //}



        if(crossSectionImage != null){
            if(csi.getWidth() != crossSectionImage.getWidth()){
                delay_time = delay_time * 2;
            }

        }else if(crossSectionImage_single != null){
            if(csi.getWidth() != crossSectionImage_single.getWidth()){
                delay_time = delay_time * 2;
            }
        }


        return csi;

    }

    int delay_time = 0;

    private void createImage(){
        //int delay_time = 5; //ms

        long now_time = System.currentTimeMillis();
        orth_timer = orth_timer - now_time;

        if(orth_timer <= 0){
            CrossSectionThread cst = new CrossSectionThread();
            cst.start();
            orth_timer = System.currentTimeMillis() + delay_time;

        }else{
            orth_timer = System.currentTimeMillis() + delay_time;

        }

    }


    private void setCrossSectionImage(){

        ImagePlus buffImage = this.createCrossSectionImage();

        if(buffImage != null) {

            if (chSize > 1) {
                if ((crossSectionImage != null) && (crossSectionImage.isVisible())) {
                    //crossSectionImage.setImage(buffImage); //->CompositeImageで配列の長さ違いエラーが出る場合がある

                    crossSectionImage.setStack(buffImage.getStack(), chSize, 1,1);

                    //crossSectionImage.updateAndDraw();
                }
            } else {
                if ((crossSectionImage_single != null) && (crossSectionImage_single.isVisible())) {
                    //crossSectionImage_single.setImage(buffImage);
                    crossSectionImage_single.setStack(buffImage.getStack(), chSize,1,1);

                    //crossSectionImage_single.updateAndDraw();
                }
            }
            syncZYZ();
        }
    }


    class CrossSectionThread extends Thread{
        public void run(){
            setCrossSectionImage();
        }
    }


    private void syncLut(CompositeImage a, CompositeImage b){
        LUT[] a_lut = a.getLuts();
        LUT[] b_lut = b.getLuts();

        boolean check = true;


        out:for(int i = 0; i < a_lut.length; i++){
            if(a_lut[i] != b_lut[i]){
                check = false;
                break out;
            }
        }

        if(a.getMode() == IJ.GRAYSCALE){
            check = true;
        }

        if(check == false){
            b.setLuts(a_lut);
        }

    }

    private void changeCrossSectionImage(){
        if(mainImage.getRoi() != null) {
            Roi r = (Roi) mainImage.getRoi().clone();
            if (r.isLine()) {
                //if (r.getLength() > 0) {
                if(r.getContainedFloatPoints().npoints > 2){
                    if (chSize > 1) {
                        if ((crossSectionImage != null) && (crossSectionImage.isVisible())) {
                            this.createImage();
                        }
                    } else {
                        if ((crossSectionImage_single != null) && (crossSectionImage_single.isVisible())) {
                            this.createImage();
                        }
                    }

                }
            }
        }
    }

    private void syncZYZ(){
        int cc =  mainImage.getC();

        /// Mode 同期 ///
        if(mainImage.isComposite()){
            CompositeImage ci_imp = (CompositeImage)mainImage;


            //Channelsで切り替えたときのみの動作。
            if(ci_imp.getMode() != crossSectionImage.getMode()) {
                crossSectionImage.setMode(ci_imp.getMode());

            }else{ // なにかうごいたときいつでも


            }

            ///ChannelsでComposite時にChannelを変えた場合の動作

            boolean[] active = ci_imp.getActiveChannels();
            boolean[] active_xz = crossSectionImage.getActiveChannels();

            for(int i = 0; i < active_xz.length; i++){
                active_xz[i] = active[i];
            }

            ///////////////

            // lut set //
            syncLut(ci_imp,crossSectionImage);

            crossSectionImage.setC(cc);

        }else{

            LUT imp_l = mainImage.getProcessor().getLut();
            LUT xz_l = crossSectionImage_single.getProcessor().getLut();

            if(xz_l != old_lut){
                crossSectionImage_single.getProcessor().setLut(imp_l);
                //
                // crossSectionImage_single.updateAndDraw();
            }

        }

    }


    @Override
    public void imageOpened(ImagePlus imp) {

    }

    @Override
    public void imageClosed(ImagePlus imp) {

    }

    @Override
    public void imageUpdated(ImagePlus imp) {
        if(oldCh != mainImage.getC()){
            if(crossSectionImage != null) {
                crossSectionImage.setC(mainImage.getC());
            }
            oldCh = mainImage.getC();
        }

        Overlay ol = overlayManager.getOverlay(1, 1, mainImage.getT());
        mainImage.setOverlay(ol); //Ch,Z positionのoverlayは無視

        if(oldT != mainImage.getT()) {
            if(ui.isInterlockTime()) {
                this.changeActiveRoi();
            }
            oldT = mainImage.getT();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

        if(e.getSource() == ic){

            Roi r = mainImage.getRoi();
            if(r != null) {
                if(r.getState() != Roi.CONSTRUCTING) {
                    if (r.isLine()) {


                        if (chSize > 1) {
                            if (crossSectionImage == null) {
                                crossSectionImage = (CompositeImage) this.createCrossSectionImage();
                                crossSectionImage.show();

                            } else {
                                if (!crossSectionImage.isVisible()) {
                                    crossSectionImage = (CompositeImage) this.createCrossSectionImage();
                                    crossSectionImage.show();
                                }
                            }
                        } else {
                            if (crossSectionImage_single == null) {
                                crossSectionImage_single = this.createCrossSectionImage();
                                crossSectionImage_single.show();

                            } else {
                                if (!crossSectionImage_single.isVisible()) {
                                    crossSectionImage_single = this.createCrossSectionImage();
                                    crossSectionImage_single.show();
                                }

                            }
                        }
                        this.createImage();

                        if (ui.isAddModeSelected()) {
                            if (!mainImage.getOverlay().contains(r)) {
                                mainImage.getOverlay().add(r);
                            }
                        }

                        if (ui.isInterlockTime()) {
                            this.copyOverlayRoi();
                            selectedRoiIndex = ((OverlaySM) mainImage.getOverlay()).getIndex(mainImage.getRoi());
                        }

                    }
                }
            }

        }


    }

    private  void copyOverlayRoi(){
        int currentT = mainImage.getT();

        OverlaySM buffOverlay = (OverlaySM)mainImage.getOverlay();
        int selectedRoiIndex = buffOverlay.getIndex(mainImage.getRoi());

        for(int t = 0; t < tSize; t++){
            if(t != (currentT -1)) {
                OverlaySM tBuff = overlayManager.getOverlay(1, 1, t+1);
                tBuff.clear();
                buffOverlay.getOriginalList().forEach(roi -> {
                    tBuff.add((Roi)roi.clone());
                });
            }
        }


    }

    private void changeActiveRoi(){
        OverlaySM buff = (OverlaySM) mainImage.getOverlay();
        mainImage.getRoi().setImage(null);
        mainImage.setRoi(buff.get(selectedRoiIndex));
    }


    @Override
    public void mouseEntered(MouseEvent e) {

        boolean toolCheck = this.checkToolbox();
        if(toolCheck == false){
            //IJ.showMessage("Notice", "Please select Line or Segmented Line Tool");
            //IJ.setTool(Toolbar.LINE);
        }

        //IJ.setTool(Toolbar.LINE);
    }

    public boolean checkToolbox(){
        boolean toolCheck = false;
        ArrayList<Integer> toolList = new ArrayList<>();

        toolList.add(Toolbar.LINE);
        toolList.add(Toolbar.POLYLINE);
        toolList.add(Toolbar.FREELINE);

        for(int i = 0; i < toolList.size(); i++){
            if(Toolbar.getToolId() == toolList.get(i)){
                toolCheck = true;
                break;
            }
        }
        return toolCheck;
    }



    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseDragged(MouseEvent e) {

        if(e.getSource() == ic){

            Roi r = mainImage.getRoi();
            if(r != null) {
                if(r.getState() != Roi.CONSTRUCTING) {
                    if (r.isLine()) {
                        this.changeCrossSectionImage();
                        //this.setCrossSectionImage();
                    }
                }
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {


        if(tSize > 1) {
            int rot = e.getWheelRotation();
            IJ.showStatus("rot:" + rot);
            int sign = 1;
            if (rot < 0) sign = -1;


            int slic = mainImage.getT() + sign;
            mainImage.setT(slic);

            this.changeCrossSectionImage();
        }
        /*
        for(int i = 0; i < Math.abs(rot); i++) {
            int slic = mainImage.getT() + sign;
            mainImage.setT(slic);

            this.changeCrossSectinImage();


        }
        */
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {
        if(e.getSource() == ic){
            if(newStackImage == null){
                newStackImage = new ImageStack();
            }
        }

    }

    @Override
    public void windowClosing(WindowEvent e) {
        ui.close();
        this.removeListener();
        this.close();
    }
}
