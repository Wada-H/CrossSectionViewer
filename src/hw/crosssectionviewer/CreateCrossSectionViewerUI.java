package hw.crosssectionviewer;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.CanvasResizer;
import ij.plugin.HyperStackConverter;
import ij.process.ImageProcessor;
import ij.process.LUT;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;

public class CreateCrossSectionViewerUI extends AnchorPane {


    int chSize;
    int zSize;
    int tSize;
    int imgWidth;
    int imgHeight;
    int imgDepth;


    String fileName;
    ImagePlus mainImage;

    OverlayManager overlayManager;

    String strokeColor;

    int currentC;
    int currentZ;
    int currentT;


    String title;
    String imageFileDir;
    String imageFileName;

    File saveFileName;
    File loadFileName;


    Scene scene;
    JFXPanel jfxPanel;
    FXMLLoader loader;


    @FXML private Button createImageButton;
    @FXML private Button createXZimageButton;
    @FXML private RadioButton addModeRadio;
    @FXML private RadioButton editModeRadio;
    @FXML private CheckBox interlockTimeCheck;

    @FXML private ChoiceBox<String> cbProjectionMethod;
    ObservableList<String> cbMethodsList = FXCollections.observableArrayList("Max","Ave");

    public CreateCrossSectionViewerUI(ImagePlus img, String file_name){
        fileName = file_name;
        mainImage = img;
        if(mainImage.getOriginalFileInfo() != null){
            imageFileName = mainImage.getOriginalFileInfo().fileName;
            imageFileDir = mainImage.getOriginalFileInfo().directory;

        }else{
            imageFileName = mainImage.getTitle();
            imageFileDir = System.getProperty("user.home");

        }

        // 何かの拍子でnullになると保存ができなくなるので保険 //
        if(imageFileName == null) {
            imageFileName = "NewData";
        }
        if(imageFileDir == null) {
            imageFileDir = "./";
        }
        //


        this.getBasicInformation();
    }

    public void getBasicInformation(){
        chSize = mainImage.getNChannels();
        zSize = mainImage.getNSlices();
        tSize = mainImage.getNFrames();
        imgWidth = mainImage.getWidth();
        imgHeight = mainImage.getHeight();
        imgDepth = mainImage.getBitDepth();

    }

    public JFXPanel getFXML(){
        Pane result = new Pane();
        jfxPanel = new JFXPanel();
        loader = new FXMLLoader();
        loader.setRoot(this);
        loader.setController(this);
        //loader.setController(new Test()); //こんな書き方でもいける。ただし、今回の場合は分離するほうが面倒
        try {
            //result = FXMLLoader.load(getClass().getResource(fileName));
            result = loader.load(getClass().getResourceAsStream(fileName));
            scene = new Scene(result,result.getPrefWidth(),result.getPrefHeight());
            //scene = new Scene(result);
            jfxPanel.setScene(scene);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }



        return jfxPanel;
    }


    @FXML
    private void initialize(){
        cbProjectionMethod.setItems(cbMethodsList);
        cbProjectionMethod.setValue(cbMethodsList.get(0));

    }


    public boolean isAddModeSelected(){
        return addModeRadio.isSelected();
    }


    public boolean isEditModeSelected(){
        return editModeRadio.isSelected();
    }

    public boolean isInterlockTime(){
        return interlockTimeCheck.isSelected();
    }

    public int getModeId(){
        int id = 0;
        if(cbProjectionMethod.getValue() == cbMethodsList.get(1)){
            id = 1;
        }
        return id;
    }

    public void setOverlayManager(OverlayManager om){
        overlayManager = om;
    }

    @FXML
    private void createImage(){


        ArrayList<ArrayList<ImagePlus>> buffImageArray = new ArrayList<>();
        for(int i = 0; i < tSize; i++){
            buffImageArray.add(new ArrayList<>());
        }

        IntStream intStream = IntStream.range(0, tSize);
        intStream.forEach(t ->{
            CreateCrossSectionImage ccsi = new CreateCrossSectionImage(mainImage);
            ccsi.setMode(this.getModeId());
            ccsi.setT(t+1);

            ArrayList<ImagePlus> buffimglist = new ArrayList<>();
            OverlaySM os = overlayManager.getOverlay(1,1, t+1);
            os.getList().forEach(r ->{
                buffimglist.add(ccsi.createCrossSectionImage(r));
            });
            buffImageArray.add(t, buffimglist);
        });

        final int maxWidth = getMaxWidth(buffImageArray.get(0));
        final int maxHeight = buffImageArray.get(0).get(0).getHeight();

        ImageStack stackImage = new ImageStack(maxWidth, maxHeight);

        CanvasResizer cr = new CanvasResizer();
        buffImageArray.forEach(czimg ->{
            czimg.forEach(img ->{
                int x_p = (maxWidth - img.getWidth()) / 2 ;
                ImageStack is = img.getImageStack();
                for(int s = 0; s < is.getSize(); s++) {
                    ImageProcessor ip = cr.expandImage(is.getProcessor(s+1), maxWidth, maxHeight, x_p, 0);
                    stackImage.addSlice(ip);
                }
            });
        });

        ImagePlus result = new ImagePlus();
        result.setStack(stackImage);
        Calibration buffCal = mainImage.getCalibration().copy();
        buffCal.pixelHeight = buffCal.pixelDepth / ((double)result.getHeight() / mainImage.getNSlices());
        buffCal.pixelDepth = 1;
        result.setCalibration(buffCal);
        result.setFileInfo(mainImage.getFileInfo());
        result.setDimensions(chSize,(stackImage.getSize()/(chSize*tSize)),tSize);
        result.setTitle("CrossSectionStack");

        if(chSize > 1) {
            result = HyperStackConverter.toHyperStack(result, chSize, (stackImage.getSize() / (chSize*tSize)), tSize, "xyczt", "grayscale");


            CompositeImage ci = (CompositeImage) result;
            CompositeImage ci_main = (CompositeImage) mainImage;

            ci.setMode(ci_main.getMode());
            ci.setLuts(ci_main.getLuts());
            ci.show();
        }else{
            result.setLut(mainImage.getLuts()[0]);
            result.show();
        }
    }


    @FXML
    private void createXZimage(){
        OverlaySM os = new OverlaySM();
        for(int y = 0; y < mainImage.getHeight(); y++){
            Roi r = new Line(0,y,mainImage.getWidth()-1,y);
            os.add(r);
        }

        Calibration cal = mainImage.getCalibration();

        double calx = cal.pixelWidth;
        double calz = cal.pixelDepth;
        double az = calz / calx;
        double thickness = az;

        ImageStack stackImage = new ImageStack(mainImage.getWidth(), (int)(Math.round(mainImage.getNSlices()*thickness)));

        CreateCrossSectionImage ccsi = new CreateCrossSectionImage(mainImage);
        ccsi.setMode(this.getModeId());
        for(int t = 0; t < tSize; t++){
            ccsi.setT(t+1);
            final int tPosition = t+1;

            ArrayList<ImagePlus> buffImageArray = new ArrayList<>(mainImage.getHeight());
            for(int y = 0; y < mainImage.getHeight(); y++){
                buffImageArray.add(new ImagePlus());
            }

            IntStream intStream = IntStream.range(0, mainImage.getHeight());
            intStream.parallel().forEach(h ->{
                buffImageArray.get(h).setImage(ccsi.createCrossSectionImage(os.get(h)));
                //buffImageArray.add(h, ccsi.createCrossSectionImage(os.get(h))); //これだとバラバラに入る
            });

            buffImageArray.forEach(czimg ->{
                ImageStack is = czimg.getImageStack();
                for(int s = 0; s < is.getSize(); s++) {
                    stackImage.addSlice(is.getProcessor(s+1));
                }
            });
        }


        ImagePlus result = new ImagePlus();
        result.setStack(stackImage);
        Calibration newCal = mainImage.getCalibration().copy();
        newCal.pixelHeight = newCal.pixelDepth / ((double)result.getHeight() / mainImage.getNSlices());
        newCal.pixelDepth = mainImage.getCalibration().pixelHeight;
        result.setCalibration(newCal);
        result.setFileInfo(mainImage.getFileInfo());
        result.setDimensions(chSize,mainImage.getHeight(),tSize);
        result.setTitle("CrossSectionStackXZ");

        if(chSize > 1) {
            result = HyperStackConverter.toHyperStack(result, chSize, (stackImage.getSize() / (chSize*tSize)), tSize, "xyczt", "grayscale");


            CompositeImage ci = (CompositeImage) result;
            CompositeImage ci_main = (CompositeImage) mainImage;

            ci.setMode(ci_main.getMode());
            ci.setLuts(ci_main.getLuts());
            ci.show();
        }else{
            result.setLut(mainImage.getLuts()[0]);
            result.show();
        }


    }


    private int getMaxWidth(ArrayList<ImagePlus> imgList){
        int result = 0;

        result = imgList.parallelStream().parallel()
                .map(img ->{
                    return img.getWidth();
                })
                .max(Comparator.naturalOrder())
                .get();

        return result;
    }


    public void close(){
        Platform.setImplicitExit(false);
    }
}
