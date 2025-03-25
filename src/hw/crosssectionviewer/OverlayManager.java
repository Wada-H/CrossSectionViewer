package hw.crosssectionviewer;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

//Ç¢ÇÈÇÃÇ©ÅHÅH

public class OverlayManager {

	List<OverlaySM> overlayList;

	ImagePlus imp;
	int cSize;
	int zSize;
	int tSize;
	int totalSize;
	int currentSlice;
	
	public OverlayManager(ImagePlus im){
		imp = im;
		totalSize = imp.getStackSize();
		cSize = imp.getNChannels();
		zSize = imp.getNSlices();
		tSize = imp.getNFrames();
		currentSlice = imp.getCurrentSlice();
		overlayList = new ArrayList<OverlaySM>(totalSize);
		this.clearList();
	}
	
	public void clearList(){
		for(int i = 0; i < totalSize; i++){
			overlayList.add(new OverlaySM());
			//overlayList.get(i).drawNames(true);
			overlayList.get(i).drawLabels(true);
		}
	}
	
	public void setDimension(int c, int z, int t){
		cSize = c;
		zSize = z;
		tSize = t;
	}
	
	public OverlaySM getOverlay(int c, int z, int t){
		int index = imp.getStackIndex(c, z, t) - 1;
		return overlayList.get(index);
	}
	
	public void setOverlay(Overlay[] ol){
		if(ol.length == totalSize){
			this.clearList();
			IntStream i_stream = IntStream.range(0, totalSize);
			i_stream.parallel().forEach(i -> {
				OverlaySM buffArray = new OverlaySM();
				Roi[] roiArray = ol[i].toArray();
				for(int n = 0; n < roiArray.length; n++){
					buffArray.add(roiArray[n]);
				}
				overlayList.add(i, buffArray);
				overlayList.get(i).drawLabels(true);
			});
		}
	}
	
	public void setOverlay(Overlay[][][] ol){
		int olc = ol.length;
		int olz = ol[0].length;
		int olt = ol[0][0].length;
		
		//System.out.println("olc,olz,olt:" + olc + "," + olz + "," + olt + "");
		
		if((olc*olz*olt) == totalSize){
			this.clearList();
			IntStream i_stream = IntStream.range(0, tSize);
			i_stream.forEach(t -> {
				for(int z = 0; z < zSize; z++){
					for(int c = 0; c < cSize; c++){
						int index = imp.getStackIndex(c+1, z+1, t+1);
						OverlaySM buffArray = new OverlaySM();
						Roi[] roiArray = ol[c][z][t].toArray();
						for(int n = 0; n < roiArray.length; n++){
							buffArray.add(roiArray[n]);
						}
						overlayList.add(index-1, buffArray);
						overlayList.get(index-1).drawLabels(true);
					}
				}
			});
		}
	}
	
	
	public OverlaySM getOverlay(int index){
		return overlayList.get(index);
	}
	
	public OverlaySM[] getOverlayFlatArray(){
		OverlaySM[] overlayArray = new OverlaySM[totalSize];
		for(int i = 0; i < totalSize; i++ ){
			overlayArray[i] = overlayList.get(i).duplicate();
		}
		return overlayArray;
	}
	
	public void setStrokColor(Color c) {
		overlayList.parallelStream().forEach(ov ->{ov.setStrokeColor(c);});
	}
	
}
