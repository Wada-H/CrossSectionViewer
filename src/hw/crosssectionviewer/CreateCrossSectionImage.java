package hw.crosssectionviewer;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import ij.plugin.MontageMaker;
import ij.plugin.Straightener;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.util.ArrayList;
import java.util.stream.IntStream;

public class CreateCrossSectionImage {

    ImagePlus mainImage;
    Roi currentRoi;

    int chSize;
    int zSize;
    long creatingTime = 0;
    int mode = 0; // 0:Max projection, 1:Average projection
    int optionalT = -1;
    double accuracy = 0.02; //8bitでは0.01以下で最良, edge smoothingでは0.02以上にしない。

    boolean vertical = false;

    boolean keepRoiPoints = false;


    public CreateCrossSectionImage(ImagePlus img){
        mainImage = img;
        currentRoi = img.getRoi();
        chSize = img.getNChannels();
        zSize = img.getNSlices();
    }

    public void setMode(int modeId){
        mode = modeId;
    }

    public void convertVertical(boolean b){
        vertical = b;
    }

    public ImagePlus createCrossSectionImage(){
        Roi r = mainImage.getRoi();
        if(r != null) {
            return this.createCrossSectionImage(r);
        }else{
            return null;
        }
    }

    public void setT(int t){
        optionalT = t;
    }

    public ImagePlus createCrossSectionImage(Roi r){
        long sTime = System.currentTimeMillis();
        if(optionalT == -1){
            optionalT = mainImage.getT();
        }

        final LUT[] luts = mainImage.getLuts();
        final int currentT = optionalT;
        final Roi roi = (Roi)r.clone(); //これ肝。

        ImagePlus[] buff = new ImagePlus[chSize];
        ImagePlus resultImage = new ImagePlus();
        short[][][] ppArrays = new short[chSize][zSize][];

        //boolean averageHorizontally = Prefs.verticalProfile || IJ.altKeyDown();

        IntStream cStream = IntStream.range(0, chSize);
        cStream.parallel().forEach(c ->{
            ArrayList<ArrayList<Short>> ppArray = new ArrayList<ArrayList<Short>>();

            IntStream zStream = IntStream.range(0, zSize);
            final int chIndex = c;


            ImagePlus[] zBuff = new ImagePlus[zSize];

            zStream.parallel().forEach(z ->{//ここうまくいかないかも ->やはり並列は工夫がいりそう
                int index = mainImage.getStackIndex(chIndex+1, z+1, currentT);
                zBuff[z] = new ImagePlus();
                ImageProcessor buffIp = mainImage.getStack().getProcessor(index);
                zBuff[z].setProcessor(buffIp);
                zBuff[z].setRoi(roi);
                //ProfilePlot pp = new ProfilePlot(zBuff[z], averageHorizontally);// -> 線幅が太くなると遅いのでStraightenerと思ったけど、もろもろをProfilePlotが面倒見ているのでこれがいいかも ->Straitenen.javaでバグがあるため使用しない.

                if(mode == 0) {
                    ppArrays[chIndex][z] = this.convertShortArray(this.getLineMaxDouble(zBuff[z]));
                }else if(mode == 1){
                    ppArrays[chIndex][z] = this.convertShortArray(this.getLineAveDouble(zBuff[z]));
                }

            });

            if(vertical == false) {
                buff[c] = this.convertPPtoImage2(ppArrays[chIndex]);
            }else{
                buff[c] = this.convertPPtoImage2V(ppArrays[chIndex]);
            }
            buff[c].setLut(luts[c]);
        });

        ImageStack buffStack = new ImageStack(buff[0].getWidth(),buff[0].getHeight());
        for (ImagePlus img : buff) {
            if((buffStack.getWidth() == img.getWidth())&&(buffStack.getHeight() == img.getHeight())) {
                buffStack.addSlice(img.getProcessor());
            }
        }


        resultImage.setStack(buffStack);
        resultImage.setTitle("CrossSectionView");

        //ImagePlus sw_imp = HyperStackConverter.toHyperStack(resultImage, chSize, 1, 1,"xyczt","grayscale");

        ImagePlus sw_imp = null;
        sw_imp = resultImage;
        sw_imp.setDimensions(chSize, 1, 1);
        Calibration cal = mainImage.getCalibration().copy();
        double calibratedValue = cal.pixelDepth / ((double)sw_imp.getHeight() / mainImage.getNSlices());
        cal.pixelHeight = calibratedValue;
        cal.pixelDepth = 0;
        sw_imp.setCalibration(cal);

        if (chSize > 1) {
            sw_imp = HyperStackConverter.toHyperStack(resultImage, chSize, 1, 1, "xyczt", "grayscale");

        }
        this.syncZYZ(sw_imp);


        long eTime = System.currentTimeMillis();
        creatingTime = (eTime - sTime);

        //System.out.println("createImage" + creatingTime + "msec");

        return sw_imp;
    }


    private double[] getLineAveDouble(ImagePlus img) {
        double[] profile;
        Roi r = (Roi)img.getRoi().clone();
        int strokeWidth = (int)Math.round(r.getStrokeWidth());
        r.setStrokeWidth(0);

        PolygonRoi pr  = new PolygonRoi(r.getInterpolatedPolygon(), Roi.POLYLINE);

        /* r.getFloatPolygonをすることでfitSpline後の点数をとってきているためfitsplineは不要
        System.out.println(pr.getNCoordinates());
        if(r.getType() == Roi.POLYLINE){

            if(((PolygonRoi)r).isSplineFit()){
                System.out.println("ここ");
                pr.fitSpline();
            }
        }
        */

        pr.setStrokeWidth(strokeWidth);
        img.setRoi(pr);



        if(strokeWidth < 1){
            strokeWidth = 1;
        }

        ImageProcessor ip1 = (new Straightener()).straightenLine(img,strokeWidth);

        if(keepRoiPoints){
            img.setRoi(r);
            ip1 = this.straightenLine(img, strokeWidth);
        }
        ImageProcessor ip2 = ip1;

        if (ip2==null) return new double[0];
        int width = ip2.getWidth();
        int height = ip2.getHeight();
        profile = new double[width];
        ip2.setInterpolate(false);


        // ->多分こっちのほうが速い
        IntStream widthStream = IntStream.range(0, width);
        widthStream.parallel().forEach(x ->{
            float sumValue = 0.0f;
            for(int y = 0; y < height; y++){
                float v = ip2.getf(x, y);

                sumValue = sumValue + v;
            }
            profile[x] = sumValue / height;
        });




        return profile;
    }


    private double[] getLineMaxDouble(ImagePlus img) {
        double[] profile;
        Roi r = (Roi)img.getRoi().clone();//ここcloneしないと元のRoiのstrokeWidthを変えてしまう
        int strokeWidth = (int)Math.round(r.getStrokeWidth());
        r.setStrokeWidth(0);
        PolygonRoi pr  = new PolygonRoi(r.getInterpolatedPolygon(), Roi.POLYLINE);

        //pr.fitSpline(); //2点以上の場合意味があるかも
        /* r.getFloatPolygonをすることでfitSpline後の点数をとってきているためfitsplineは不要
        System.out.println(pr.getNCoordinates());
        if(r.getType() == Roi.POLYLINE){
            if(((PolygonRoi)r).isSplineFit()){
                System.out.println("ここ");

                pr.fitSpline();
            }
        }
        */


        pr.setStrokeWidth(strokeWidth);
        img.setRoi(pr);

        if(strokeWidth < 1){
            strokeWidth = 1;
        }

        ImageProcessor ip1 = (new Straightener()).straightenLine(img,strokeWidth);

        if(keepRoiPoints){
            img.setRoi(r);
            ip1 = this.straightenLine(img, strokeWidth);
        }
        ImageProcessor ip2 = ip1;


        if (ip2==null) return new double[0];
        int width = ip2.getWidth();
        int height = ip2.getHeight();
        profile = new double[width];
        ip2.setInterpolate(false);

        IntStream widthStream = IntStream.range(0, width);
        widthStream.parallel().forEach(x ->{
            float maxValue = 0.0f;
            for(int y = 0; y < height; y++){
                float v = ip2.getf(x, y);

                if(maxValue < v) maxValue = v;
            }
            profile[x] = maxValue;
        });


        return profile;
    }

    private short[] convertShortArray(double[] doubleArray){
        //IntStream intStream = IntStream.range(0, doubleArray.length);
        short[] result = new short[doubleArray.length];
        //intStream.parallel().forEach(i->{
        //    result[i] = (short)Math.rint(doubleArray[i]);
        //});
        for(int i = 0; i < doubleArray.length; i++){
            result[i] = (short)Math.rint(doubleArray[i]);
        }
        return result;
    }



    private ImagePlus convertPPtoImage2(short[][] ppArray){
        Calibration cal = mainImage.getCalibration();

        double calx = cal.pixelWidth;
        double caly = cal.pixelHeight;
        double calz = cal.pixelDepth;
        double az = calz / calx;
        //int thickness = (int)Math.floor(az);
        double thickness = az;
        if(thickness == 0){
            thickness = 1;
        }
        //double gsd = (calx * calz) / 2.0; //この値でいいのか？
        double gsd = thickness * 0.4;


        MontageMaker mm = new MontageMaker();
/*
        ImageStack stack = new ImageStack(ppArray.get(0).length, 1);
        ppArray.forEach(array ->{
            stack.addSlice("", array);
        });
*/

        ImageStack stack = new ImageStack(ppArray[0].length, 1, ppArray.length);
        IntStream intStream = IntStream.range(0, ppArray.length);
        intStream.parallel().forEach(i ->{
            stack.setPixels(ppArray[i], i+1);

        });

        ImagePlus buffStack = new ImagePlus();
        buffStack.setStack(stack);

        int scale = 1;
        int row = 1;
        int column = buffStack.getStackSize();
        int first = 1;
        int last = buffStack.getStackSize();
        int inc = 1; //increment
        int borderWidth = 0;
        boolean labels = false;

        ImagePlus mImage = mm.makeMontage2(buffStack,row,column,scale,first,last,inc,borderWidth,labels);
        ImageProcessor resized_p = this.resizeX(mImage, thickness, gsd);

        //double min = resized_p.getMin();
        //double max = resized_p.getMax();
        //resized_p.setMinAndMax(min, max);

        ImagePlus result = new ImagePlus();
        result.setProcessor(resized_p);

        return result;

    }

    private ImagePlus convertPPtoImage2V(short[][] ppArray){
        Calibration cal = mainImage.getCalibration();

        double calx = cal.pixelWidth;
        double calz = cal.pixelDepth;
        double az = calz / calx;
        double thickness = az;
        if(thickness == 0){
            thickness = 1;
        }
        //double gsd = (calx * calz) / 2.0;//この値でいいのか？ ->だめっぽい
        double gsd = thickness * 0.4; //ZTinterpolationでは1/2つまり0.5


        MontageMaker mm = new MontageMaker();


        ImageStack stack = new ImageStack(1, ppArray[0].length, ppArray.length);
        IntStream intStream = IntStream.range(0, ppArray.length);
        intStream.parallel().forEach(i ->{
            stack.setPixels(ppArray[i], i+1);

        });

        ImagePlus buffStack = new ImagePlus();
        buffStack.setStack(stack);

        int scale = 1;
        int row = buffStack.getStackSize();
        int column = 1;
        int first = 1;
        int last = buffStack.getStackSize();
        int inc = 1; //increment
        int borderWidth = 0;
        boolean labels = false;

        ImagePlus mImage = mm.makeMontage2(buffStack,row,column,scale,first,last,inc,borderWidth,labels);
        ImageProcessor resized_p = this.resizeY(mImage, thickness, gsd);

        ImagePlus result = new ImagePlus();
        result.setProcessor(resized_p);

        return result;

    }


    private ImagePlus convertPPtoImage(ArrayList<short[]> ppArray){ //horizon
        Calibration cal = mainImage.getCalibration();

        double calx = cal.pixelWidth;
        double caly = cal.pixelHeight;
        double calz = cal.pixelDepth;
        double az = calz / calx;
        double gsd = (calx * calz) / 2.0;
        //int thickness = (int)Math.floor(az);
        double thickness = az;
        if(thickness == 0){
            thickness = 1;
        }

        MontageMaker mm = new MontageMaker();
/*
        ImageStack stack = new ImageStack(ppArray.get(0).length, 1);
        ppArray.forEach(array ->{
            stack.addSlice("", array);
        });
*/

        ImageStack stack = new ImageStack(ppArray.get(0).length,1,ppArray.size());
        IntStream intStream = IntStream.range(0, ppArray.size());
        intStream.parallel().forEach(i ->{
            stack.setPixels(ppArray.get(i), i+1);
        });

        ImagePlus buffStack = new ImagePlus();
        buffStack.setStack(stack);

        int scale = 1;
        int row = 1;
        int column = buffStack.getStackSize();
        int first = 1;
        int last = buffStack.getStackSize();
        int inc = 1; //increment
        int borderWidth = 0;
        boolean labels = false;

        ImagePlus mImage = mm.makeMontage2(buffStack,row,column,scale,first,last,inc,borderWidth,labels);
        ImageProcessor resized_p = this.resizeX(mImage, thickness, gsd);

        //double min = resized_p.getMin();
        //double max = resized_p.getMax();
        //resized_p.setMinAndMax(min, max);

        ImagePlus result = new ImagePlus();
        result.setProcessor(resized_p);

        return result;
    }



    private ImageProcessor resizeX(ImagePlus imp, double thickness, double gsd){
        int width = imp.getWidth();
        int height = imp.getHeight();
        GaussianBlur gb_filter = new GaussianBlur();


        ImageProcessor pi_ip = imp.getProcessor();
        ImageProcessor resized_p = pi_ip.resize(width, (int)(Math.round((height * thickness))), true);
        //gb_filter.blurGaussian(resized_p, 0, (thickness * 0.4), 0.02);
        gb_filter.blurGaussian(resized_p,0, gsd, accuracy);

        //MaximumZProjection maximumZProjection = new MaximumZProjection();

        //LucyRichardsonをかけると0で割る場合に不具合が出るため、画像全体から1引いてさらに1を足すという処理をいれる(backgroundにかならず1が入る)
        // 元のmethodで同じように引いてから足すと意味がない結果になる。足すだけならいい感じになるが、辻褄が合わない可能性が出てくる
        //resized_p.subtract(1.0);
        //resized_p.add(1.0);

        LucyRichardson lucyRichardson = new LucyRichardson(resized_p);
        lucyRichardson.setGaussianBlurProperties(0, gsd, accuracy);
        ImageProcessor deconvolutionImage = lucyRichardson.getProcessedImage(0, 10);

        System.out.println("thickness : " + thickness);
        System.out.println("gsd : " + gsd);
        int depthNum = mainImage.getBitDepth();
        switch (depthNum){
            case 8:
                deconvolutionImage = deconvolutionImage.convertToByteProcessor(false);
                break;
            case 16:
                deconvolutionImage = deconvolutionImage.convertToShortProcessor(false);
                break;
        }

        //ImagePlus testImage = new ImagePlus();
        //testImage.setProcessor(deconvolutionImage);
        //testImage.setTitle("resizeX");
        //testImage.show();

        return deconvolutionImage;
    }

    private ImageProcessor resizeY(ImagePlus imp, double thickness, double gsd){
        int width = imp.getWidth();
        int height = imp.getHeight();
        GaussianBlur gb_filter = new GaussianBlur();

        ImageProcessor pi_ip = imp.getProcessor();
        ImageProcessor resized_p = pi_ip.resize((int)(Math.round((width * thickness))), height, true);
        //gb_filter.blurGaussian(resized_p, (thickness * 0.4), 0, 0.02);
        gb_filter.blurGaussian(resized_p, gsd, 0, accuracy);
        //MaximumZProjection maximumZProjection = new MaximumZProjection();


        return resized_p;
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
        syncMinMax(a, b);
    }

    private void syncMinMax(CompositeImage donor, CompositeImage target){
        target.setDisplayRange(donor.getDisplayRangeMin(),donor.getDisplayRangeMax());
    }

    private void syncZYZ(ImagePlus img){
        int cc =  mainImage.getC();

        /// Mode 同期 ///
        if(mainImage.isComposite()){
            CompositeImage ci_imp = (CompositeImage)mainImage;
            CompositeImage crossSectionImage = (CompositeImage)img;

            //Channelsで切り替えたときのみの動作。
            if(ci_imp.getMode() != crossSectionImage.getMode()) {
                if(ci_imp.getMode() == IJ.COMPOSITE){

                    crossSectionImage.setMode(ci_imp.getMode());

                }else if(ci_imp.getMode() == IJ.COLOR){

                    crossSectionImage.setMode(ci_imp.getMode());

                }else if(ci_imp.getMode() == IJ.GRAYSCALE){
                    crossSectionImage.setMode(ci_imp.getMode());
                }

            }else{ // なにかうごいたときいつでも


            }

            ///ChannelsでComposite時にChannelを変えた場合の動作

            boolean[] active = ci_imp.getActiveChannels().clone();
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
            LUT xz_l = img.getProcessor().getLut();
            img.getProcessor().setLut(imp_l);

        }

    }

    public void setKeepPointNum(boolean b){// xz,yz画像を作る際に使用するROIの点数を保持するかどうか。
        keepRoiPoints = b;
    }

    // from Straightener, SheetMeshProjectionに必要
    public ImageProcessor straightenLine(ImagePlus imp, int width) {
        Roi tempRoi = imp.getRoi();
        if (!(tempRoi instanceof PolygonRoi))
            return null;
        PolygonRoi roi = (PolygonRoi)tempRoi;
        if (roi==null)
            return null;
        if (roi.getState()==Roi.CONSTRUCTING)
            roi.exitConstructingMode();
        if (roi.isSplineFit())
            roi.removeSplineFit();
        int type = roi.getType();
        int n = roi.getNCoordinates();
        double len = roi.getLength();
        //roi.fitSplineForStraightening();
        if (roi.getNCoordinates()<2)
            return null;
        FloatPolygon p = roi.getFloatPolygon();
        n = p.npoints;
        ImageProcessor ip = imp.getProcessor();
        ImageProcessor ip2 = new FloatProcessor(n, width);
        //ImageProcessor distances = null;
        //if (IJ.debugMode)  distances = new FloatProcessor(n, 1);
        float[] pixels = (float[])ip2.getPixels();
        double x1, y1;
        double x2=p.xpoints[0]-(p.xpoints[1]-p.xpoints[0]);
        double y2=p.ypoints[0]-(p.ypoints[1]-p.ypoints[0]);
        if (width==1)
            ip2.putPixelValue(0, 0, ip.getInterpolatedValue(x2, y2));
        for (int i=0; i<n; i++) {
            x1=x2; y1=y2;
            x2=p.xpoints[i]; y2=p.ypoints[i];
            //if (distances!=null) distances.putPixelValue(i, 0, (float)Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)));
            if (width==1) {
                ip2.putPixelValue(i, 0, ip.getInterpolatedValue(x2, y2));
                continue;
            }
            double dx = x2-x1;
            double dy = y1-y2;
            double length = (float)Math.sqrt(dx*dx+dy*dy);
            dx /= length;
            dy /= length;
            //IJ.log(i+"  "+x2+"  "+dy+"  "+(dy*width/2f)+"   "+y2+"  "+dx+"   "+(dx*width/2f));
            double x = x2-dy*width/2.0;
            double y = y2-dx*width/2.0;
            int j = 0;
            int n2 = width;
            do {
                ip2.putPixelValue(i, j++, ip.getInterpolatedValue(x, y));;
                //ip.drawDot((int)x, (int)y);
                x += dy;
                y += dx;
            } while (--n2>0);
        }
        if (type==Roi.FREELINE)
            roi.removeSplineFit();
        else
            imp.draw();
        if (imp.getBitDepth()!=24) {
            ip2.setColorModel(ip.getColorModel());
            ip2.resetMinAndMax();
        }
        return ip2;
    }




    public long getCreatingTime(){
        return creatingTime;
    }


    //上記より繰り返しが増える分直接imageを作ったほうがはやいかも//


}
