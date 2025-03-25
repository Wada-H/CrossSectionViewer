package hw.crosssectionviewer;

import ij.plugin.ImageCalculator;
import ij.plugin.filter.GaussianBlur;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/*
Written by Housei Wada
20230210 ver.1.0
 */

public class LucyRichardson {

    private FloatProcessor mainImage;
    private FloatProcessor blurImage;
    private FloatProcessor ratioImage;

    //Gaussian Blur Properties
    private double gsdX;
    private double gsdY;
    private double accuracy;

    // filterNum : 0 - N, とりあえず 0 = GaussianBlur.blurGaussian(resized_p, gsd, 0, accuracy);

    public LucyRichardson(ImageProcessor imp){
        mainImage = imp.convertToFloatProcessor();
    }

    public void setGaussianBlurProperties(double x, double y, double ac){
        gsdX = x;
        gsdY = y;
        accuracy = ac;
    }

    public ImageProcessor getProcessedImage(int filterNum, int repeatNum){
        GaussianBlur gaussianBlur = new GaussianBlur();
        ImageCalculator imageCalculator = new ImageCalculator();
        for(int n = 0; n < repeatNum; n++){
            blurImage = mainImage.convertToFloatProcessor();
            gaussianBlur.blurGaussian(blurImage, gsdX, gsdY, accuracy);

            ratioImage = calculate(mainImage, blurImage, Blitter.DIVIDE);
            gaussianBlur.blurGaussian(ratioImage, gsdX, gsdY, accuracy);
            mainImage = calculate(mainImage, ratioImage, Blitter.MULTIPLY);

            //ImagePlus testImage = new ImagePlus();
            //testImage.setProcessor(mainImage);
            //testImage.setTitle("#-" + String.valueOf(n));
            //testImage.show();

        }

        //ImagePlus testImage = new ImagePlus();
        //testImage.setProcessor(mainImage);
        //testImage.setTitle("Repeat#-" + String.valueOf(repeatNum));
        //testImage.show();

        return mainImage;
    }

    public FloatProcessor calculate(FloatProcessor ip1, FloatProcessor ip2, int mode){
        FloatProcessor ip3 = ip1.convertToFloatProcessor();
        ip3.copyBits(ip2, 0, 0, mode);
        ip3.resetMinAndMax();
        return ip3;
    }
}
