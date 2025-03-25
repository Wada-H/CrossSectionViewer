package hw.crosssectionviewer;

import ij.gui.Overlay;
import ij.gui.Roi;
import javafx.collections.ObservableList;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import static java.util.Comparator.comparing;

// 本家はVector使用 -> ArrayListに変更 // Housei Wada 20160523

/** An Overlay is a list of Rois that can be drawn non-destructively on an Image. */
public class OverlaySM extends Overlay{
	private ArrayList<Roi> list;
	private ArrayList<Roi> originalList;
    private boolean label;
    private boolean drawNames;
    private boolean drawBackgrounds;
    private Color labelColor;
    private Font labelFont;
    private boolean isCalibrationBar;
    
    /** Constructs an empty Overlay. */
    public OverlaySM() {
    	list = new ArrayList<Roi>();
    	originalList = new ArrayList<Roi>();
    }
    
    /** Constructs an Overlay and adds the specified Roi. */
    public OverlaySM(Roi roi) {
    	list = new ArrayList<Roi>();
    	list.add(roi);
    	
    	originalList = new ArrayList<Roi>();
    	originalList.add(roi);
    	
    }

    /** Adds an Roi to this Overlay. */
    public void add(Roi roi) {
    	list.add(roi);
    	originalList.add(roi);
    }
     
    public void add(int index, Roi roi){
    	list.add(index, roi);
    	originalList.add(index, roi);
    }
    
    public void set(int index, Roi roi){
    	list.set(index, roi);
    	originalList.set(index, roi);
    }
    
    /** Adds an Roi to this Overlay. */
    public void addElement(Roi roi) {
    	originalList.add(roi);
    	list.add(roi);
    }

    /** Removes the Roi with the specified index from this Overlay. */
    public void remove(int index) {
    	originalList.remove(list.get(index));
    	list.remove(index);
    }
    
    /** Removes the specified Roi from this Overlay. */
    public void remove(Roi roi) {
    	originalList.remove(roi);
    	list.remove(roi);
    }

   /** Removes all the Rois in this Overlay. */
    public void clear() {
    	originalList.clear();
    	list.clear();
    }

    /** Returns the Roi with the specified index or null if the index is invalid. */
    public Roi get(int index) {
    	try {
    		return (Roi)list.get(index);
    	} catch(Exception e) {
    		return null;
    	}
    }
    
    /** Returns the index of the Roi with the specified name, or -1 if not found. */
    public int getIndex(String name) {
    	if (name==null) return -1;
    	Roi[] rois = toArray();
		for (int i=rois.length-1; i>=0; i--) {
			if (name.equals(rois[i].getName()))
				return i;
		}
		return -1;
    }
    
    /** Returns 'true' if this Overlay contains the specified Roi. */
    public boolean contains(Roi roi) {
    	return list.contains(roi);
    }

    /** Returns the number of Rois in this Overlay. */
    public int size() {
    	return list.size();
    }
    
    /** Returns on array containing the Rois in this Overlay. */
    public Roi[] toArray() {
    	Roi[] array = new Roi[list.size()];
    	return (Roi[])list.toArray(array);
    }
    
    /** Sets the stroke color of all the Rois in this overlay. */
    public void setStrokeColor(Color color) {
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++)
			rois[i].setStrokeColor(color);
	}

    /** Sets the fill color of all the Rois in this overlay. */
    public void setFillColor(Color color) {
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++)
			rois[i].setFillColor(color);
	}

	/** Moves all the ROIs in this overlay. */
	public void translate(int dx, int dy) {
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++) {
			Roi roi = rois[i];
			if (roi.subPixelResolution()) {
				Rectangle2D r = roi.getFloatBounds();
				roi.setLocation(r.getX()+dx, r.getY()+dy);
			} else {
				Rectangle r = roi.getBounds();
				roi.setLocation(r.x+dx, r.y+dy);
			}
		}
	}

	/** Moves all the Rois in this overlay.
	* Marcel Boeglin, October 2013
	*/
	public void translate(double dx, double dy) {
		Roi[] rois = toArray();
		boolean intArgs = (int)dx==dx && (int)dy==dy;
		for (int i=0; i<rois.length; i++) {
			Roi roi = rois[i];
			if (roi.subPixelResolution() || !intArgs) {
				Rectangle2D r = roi.getFloatBounds();
				roi.setLocation(r.getX()+dx, r.getY()+dy);
			} else {
				Rectangle r = roi.getBounds();
				roi.setLocation(r.x+(int)dx, r.y+(int)dy);
			}
		}
	}

	/*
	* Duplicate the elements of this overlay which  
	* intersect with the rectangle 'bounds'.
	* Author: Wilhelm Burger
	* Author: Marcel Boeglin
	*/
	public Overlay crop(Rectangle bounds) {
		if (bounds==null)
			return duplicate();
		Overlay overlay2 = create();
		Roi[] allRois = toArray();
		for (Roi roi: allRois) {
			Rectangle roiBounds = roi.getBounds();
			if (roiBounds.width==0) roiBounds.width=1;
			if (roiBounds.height==0) roiBounds.height=1;
			if (bounds.intersects(roiBounds))
				overlay2.add((Roi)roi.clone());
		}
		int dx = bounds.x>0?bounds.x:0;
		int dy = bounds.y>0?bounds.y:0;
		if (dx>0 || dy>0)
			overlay2.translate(-dx, -dy);
		return overlay2;
	}

	/** Removes ROIs having positions outside of the  
	* interval defined by firstSlice and lastSlice.
	* Marcel Boeglin, September 2013
	*/
	public void crop(int firstSlice, int lastSlice) {
		for (int i=size()-1; i>=0; i--) {
			Roi roi = get(i);
			int position = roi.getPosition();
			if (position>0) {
				if (position<firstSlice || position>lastSlice)
					remove(i);
				else
					roi.setPosition(position-firstSlice+1);
			}
		}
	}

	/** Removes ROIs having a C, Z or T coordinate outside the volume
	* defined by firstC, lastC, firstZ, lastZ, firstT and lastT.
	* Marcel Boeglin, September 2013
	*/
	public void crop(int firstC, int lastC, int firstZ, int lastZ, int firstT, int lastT) {
		int nc = lastC-firstC+1, nz = lastZ-firstZ+1, nt = lastT-firstT+1;
		boolean toCStack = nz==1 && nt==1;
		boolean toZStack = nt==1 && nc==1;
		boolean toTStack = nc==1 && nz==1;
		Roi roi;
		int c, z, t, c2, z2, t2;
		for (int i=size()-1; i>=0; i--) {
			roi = get(i);
			c = roi.getCPosition();
			z = roi.getZPosition();
			t = roi.getTPosition();
			c2 = c-firstC+1;
			z2 = z-firstZ+1;
			t2 = t-firstT+1;
			if (toCStack)
				roi.setPosition(c2);
			else if (toZStack)
				roi.setPosition(z2);
			else if (toTStack)
				roi.setPosition(t2);
			else
				roi.setPosition(c2, z2, t2);
			if ((c2<1||c2>nc) && c>0 || (z2<1||z2>nz) && z>0 || (t2<1||t2>nt) && t>0)
				remove(i);
		}
	}

    /** Returns the bounds of this overlay. */
    /*
    public Rectangle getBounds() {
    	if (size()==0)
    		return new Rectangle(0,0,0,0);
    	int xmin = Integer.MAX_VALUE;
    	int xmax = -Integer.MAX_VALUE;
    	int ymin = Integer.MAX_VALUE;
    	int ymax = -Integer.MAX_VALUE;
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++) {
			Rectangle r = rois[i].getBounds();
			if (r.x<xmin) xmin = r.x;
			if (r.y<ymin) ymin = r.y;
			if (r.x+r.width>xmax) xmax = r.x+r.width;
			if (r.y+r.height>ymax) ymax = r.y+r.height;
		}
		return new Rectangle(xmin, ymin, xmax-xmin, ymax-ymin);
	}
	*/

	/** Returns a new Overlay that has the same properties as this one. */
	public OverlaySM create() {
		OverlaySM overlay2 = new OverlaySM();
		overlay2.drawLabels(label);
		overlay2.drawNames(drawNames);
		overlay2.drawBackgrounds(drawBackgrounds);
		overlay2.setLabelColor(labelColor);
		overlay2.setLabelFont(labelFont);
		return overlay2;
	}
	
	/** Returns a clone of this Overlay. */
	public OverlaySM duplicate() {
		Roi[] rois = toArray();
		OverlaySM overlay2 = create();
		for (int i=0; i<rois.length; i++)
			overlay2.add((Roi)rois[i].clone());
		return overlay2;
	}
	
	public String toString() {
    	return list.toString();
    }
    
    public void drawLabels(boolean b) {
    	label = b;
    }
    
    public boolean getDrawLabels() {
    	return label;
    }
    
    public void drawNames(boolean b) {
    	label = b;
    	drawNames = b;
		Roi[] rois = toArray();
		for (int i=0; i<rois.length; i++)
			rois[i].setIgnoreClipRect(drawNames);
    }
    
    
    public boolean getDrawNames() {
    	return drawNames;
    }

    public void drawBackgrounds(boolean b) {
    	drawBackgrounds = b;
    }
    
    public boolean getDrawBackgrounds() {
    	return drawBackgrounds;
    }

    public void setLabelColor(Color c) {
    	labelColor = c;
    }
    
    public Color getLabelColor() {
    	return labelColor;
    }

    public void setLabelFont(Font font) {
    	labelFont = font;
    }
    
    public Font getLabelFont() {
    	//if (labelFont==null && labelFontSize!=0)
    	//	labelFont = new Font("SansSerif", Font.PLAIN, labelFontSize);
    	return labelFont;
    }

    public void setIsCalibrationBar(boolean b) {
    	this.isCalibrationBar = b;
    }
    
    public boolean isCalibrationBar() {
    	return isCalibrationBar;
    }

    public int getIndex(Roi r){
    	int index = -1;
    	for(int i = 0; i < list.size(); i++){
    		Roi b = list.get(i);
    		if(r == b){
    			index = i;
    			break;
    		}
    	}
    	return index;
    }
    
    public ArrayList<Roi> getOriginalList(){
    	return originalList;
    }
    
    public void sortByOriginalList(){
		list.clear();
    	originalList.forEach(roi ->{
    		list.add(roi);
    	});
    }
    
    public void sortListByLength(String aORd){
    	ArrayList<Roi> sorted = new ArrayList<Roi>();
    	if(aORd == "a"){
        	list.stream().sorted(comparing(Roi::getLength)).forEach(r -> sorted.add(r));

    	}else if(aORd == "d"){
        	list.stream().sorted(comparing(Roi::getLength).reversed()).forEach(r -> sorted.add(r));

    	}
    	list = sorted;
    }

    static OverlaySM sortListByLength(OverlaySM o, String aORd){
    	OverlaySM result = o.duplicate();
    	result.sortListByLength(aORd);
    	return result;
    }
    
    
    public void setList(ArrayList<Roi> v) {list = v;}
    
    public void setOriginalList(ArrayList<Roi> v) {originalList = v;}
        
    public ArrayList<Roi> getList() {return list;}

    public void multipleRemove(int[] indices) {
    		ArrayList<Roi> buff = new ArrayList<Roi>();
    		for(int i: indices) {
    			Roi r = list.get(i);
    			buff.add(r);
    		}
    		buff.forEach(roi ->{
    			this.remove(roi);
    		});
    }
    
    public void multipleRemove(ObservableList<Integer> indices) {
		ArrayList<Roi> buff = new ArrayList<Roi>();
		indices.forEach(i ->{
			buff.add(list.get(i));
		});
		buff.forEach(roi ->{
			this.remove(roi);
		});
}
    
}
