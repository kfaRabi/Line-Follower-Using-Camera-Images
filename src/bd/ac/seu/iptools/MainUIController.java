/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bd.ac.seu.iptools;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author kmhasan
 */
public class MainUIController implements Initializable {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @FXML
    private Label statusLabel;
    @FXML
    private AnchorPane leftPane;
    @FXML
    private AnchorPane rightPane;
    @FXML
    private Label distanceInfo;
    
    private ImageView imageViewL;
    
    private Mat inputImage;
    private Mat outputImage;
    private Mat frame;
    int done = 0, myPos, binarized;
    MyPoint prevP = new MyPoint(0, 0);
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        binarized = 0;
        inputImage = new Mat();
        outputImage = new Mat();
        distanceInfo.setText("Click to know distance or Add an image and then click to get the route...");
        distanceInfo.setStyle("-fx-text-fill: red");
        frame = Imgcodecs.imread("input.png");
        inputImage = frame.clone();
        myPos = inputImage.cols()/2;
        distanceCalculatorPrePro();
        done = 1;
    }
    
    @FXML
    private void handleFileOpenAction(ActionEvent event) {
        try {
            FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showOpenDialog(null);
            frame = Imgcodecs.imread(file.getPath());
            inputImage = frame.clone();
            myPos = inputImage.cols()/2;
            showPath();
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".png", inputImage, buffer);
            Image imageToShow = new Image(new ByteArrayInputStream(buffer.toArray()));
            displayImage(imageToShow, leftPane);
        } catch (Exception ex) {
            Logger.getLogger(MainUIController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @FXML
    private void handleFileSaveAction(ActionEvent event) {
//        try {
//            FileChooser fileChooser = new FileChooser();
//            File file = fileChooser.showSaveDialog(null);
//            String format = file.getName().substring(file.getName().indexOf(".") + 1);
//            ImageIO.write(outputImage, format, file);
//            statusLabel.setText("Saving to " + file.getName() + " format: " + format);
//        } catch (IOException ex) {
//            Logger.getLogger(MainUIController.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }

    private void displayImage(Image image, AnchorPane anchorPane) {
        ImageView imageView = new ImageView(image);
        anchorPane.getChildren().clear();
        anchorPane.getChildren().add(imageView);
    }
    private void displayImageF(Mat m, AnchorPane anchorPane) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", m, buffer);
        Image imageToShow = new Image(new ByteArrayInputStream(buffer.toArray()));
        ImageView imageView = null;
        if(done == 0) 
            imageView = new ImageView(imageToShow);
        else {imageViewL = new ImageView(imageToShow);}
        anchorPane.getChildren().clear();
        if(done == 0)
            anchorPane.getChildren().add(imageView);
        else{    
            anchorPane.getChildren().add(imageViewL);
        }
        imageClickListener();
    }
    
    ArrayList<RedPixel> disList = new ArrayList<>();
    
    private void distanceCalculatorPrePro(){
        done = 1;
        disList.clear();

        int num = 14;
//        System.out.println("channel: "+frame.channels());
        for (int r = 0; r < frame.rows(); r++) {
            for (int c = 0; c < frame.cols(); c++) {
                double[] cc = frame.get(r, c);
                int rr = (int)(cc[2]);
                //System.out.println("r: "+rr);
                if(rr >= 150){
                    if(num < 14)
                        disList.add(new RedPixel(r, c, 2*num--, r - disList.get(disList.size()-1).r));
                    else
                        disList.add(new RedPixel(r, c, 2*num--, -1));   
                }
            }
        }
        
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        Image imageToShow = new Image(new ByteArrayInputStream(buffer.toArray()));
        displayImageF(frame, rightPane);
        
//        for(int i = 0; i < disList.size(); i++){
//            System.out.println("inches: "+disList.get(i).inches+" r: "+disList.get(i).r+" c: "+disList.get(i).c+" pixDiff: "+disList.get(i).pixDiff);
//        }
        
    }

    private void imageClickListener(){
        imageViewL.setOnMouseClicked(e -> {
            if(binarized == 1){
                drawDirection();
                binarized = 2;
                displayImageF(outputImage, rightPane);
            }
//            System.out.println("fired");
            int ro = (int)e.getY();
            int co = (int)e.getX();
            double frac, inchForOnePixel, extraPixels, distance = 0;
            int cnt = 14;
            RedPixel each = null;
            String msg = "";
            msg += "Pixel Selected (R,C) : ("+ro+","+co+")  ||  ";
            double base,hyp;
            
            for (int i = disList.size() - 1; i >= 0; i--) {
                each = disList.get(i);
                if(ro < each.r){
                    cnt = i;
                }
                else break;
            }
            if(cnt < 14) each = disList.get(cnt);
//                System.out.println("cnt: "+cnt);
            if(cnt == 14){
                //in this case, assuming the pixel difference is 50 for every 2 inches
                inchForOnePixel = 2.00/50.00;
                extraPixels = frame.rows() - ro + 40;
                distance = Math.sqrt((extraPixels * inchForOnePixel) * (extraPixels * inchForOnePixel) + 
                           (Math.abs(myPos - co) * inchForOnePixel) * (Math.abs(myPos - co) * inchForOnePixel));
//                    distance = extraPixels * inchForOnePixel;
            }
            else if(cnt == 0){
                //in this case, assuming the pixel difference is 6 for every 2 inches
                inchForOnePixel = 2.00/6.00;
                extraPixels = each.r - ro;
                base = Math.abs(co - myPos) * inchForOnePixel;
                hyp = each.inches + (extraPixels * inchForOnePixel);
                distance = Math.sqrt(hyp * hyp + base * base);
            }
            else{
                inchForOnePixel = 2.00/each.pixDiff;
                extraPixels = each.r - ro;
                base = Math.abs(co - myPos) * inchForOnePixel;
                hyp = each.inches + (extraPixels * inchForOnePixel);
                distance = Math.sqrt(hyp * hyp + base * base);
            }
            DecimalFormat fo = new DecimalFormat("00.00");
            msg += "Distance From The Robot:  "+fo.format(distance)+"  c.m";
            
            distanceInfo.setText(msg);
            if(binarized == 2){
//                System.out.println("*******");
//                Imgproc.line(outputImage, new Point(prevP.c, prevP.r), new Point(prevP.c, ro), new Scalar(new double[]{0, 255, 0}));
                estimateAndDraw(new MyPoint(ro, co));
                displayImageF(outputImage, rightPane);
            }
//            System.out.println("distance: "+distance);
        });
    }
    
    private void showPath() {
        binarized = 1;
        //displayImage(otsusThreshold(toGrayScale(inputImage)), rightPane);
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY);
        //displayImageF(frame, leftPane);
//        Imgproc.threshold(frame, outputImage, 0, 255, 8);
//        displayImageF(outputImage, leftPane);
        //Imgproc.dilate(frame, frame,new Mat());
        Imgproc.dilate(frame, frame,new Mat());
        Imgproc.blur(frame, frame,new Size(5, 5));
        Imgproc.blur(frame, frame,new Size(5, 5));
        //Imgproc.threshold(frame, outputImage, 0, 255, 8);
        //displayImageF(outputImage, leftPane);
        //Imgproc.equalizeHist(frame, outputImage);
        Imgproc.threshold(frame, outputImage, 0, 255, 8);
        frame = outputImage.clone();
        //Imgproc.Canny(frame, outputImage, 0, 255);
        //Imgproc.Canny(outputImage, outputImage, 0, 255);
        displayImageF(outputImage, rightPane);
        hor();
    }
    
    int offset = 5;
    ArrayList<MyPoint> bTW;
    ArrayList<MyPoint> wTB;
    ArrayList<MyPoint> bTWV;
    ArrayList<MyPoint> wTBV;
    ArrayList<MyPoint> bTWSelectedPix;
    ArrayList<MyPoint> wTBSelectedPix;
    
    private void hor(){
        bTW = new ArrayList<>();
        wTB = new ArrayList<>();
        bTWV = new ArrayList<>();
        wTBV = new ArrayList<>();
        bTWSelectedPix = new ArrayList<>();
        wTBSelectedPix = new ArrayList<>();
        for(int r = offset; r < outputImage.rows() - offset; r += 20){
            for(int c = offset; c < outputImage.cols() - offset; c++){
                double[] prev, cur;
                prev = outputImage.get(r, c-1);
                cur = outputImage.get(r, c);
                if(cur[0] == 0 && prev[0] == 255){
                    wTB.add(new MyPoint(r, c));
                }
                else if(cur[0] == 255 && prev[0] == 0){
                    bTW.add(new MyPoint(r, c));
                }
                prev = outputImage.get(r-1, c);
                //cur = outputImage.get(r, c);
                if(cur[0] == 0 && prev[0] == 255){
                    wTBV.add(new MyPoint(r, c));
                }
                else if(cur[0] == 255 && prev[0] == 0){
                    bTWV.add(new MyPoint(r, c));
                }
            }
        }
        formLine();
    }
    
    int thres = 2, mx, mxind, cnt,ind;
    private void formLine(){
        mx = -1;
        mxind = 0;
        ind = 0;
        double[] d = {0,0,255}; // bgr
        Imgproc.cvtColor(outputImage, outputImage, Imgproc.COLOR_GRAY2BGR);
        wTBSelectedPix = formLineN(wTB);
        bTWSelectedPix = formLineN(bTW);
//        formLineN(wTBV);
//        formLineN(bTWV);
        displayImageF(outputImage, rightPane);
        
    }
    
    private ArrayList<MyPoint> formLineN(ArrayList<MyPoint> mp){
        ArrayList<ArrayList<MyPoint>> ll = new ArrayList<>();
        mx = -1;
        mxind = 0;
        ind = 0;
        double[] d = {0,0,255}; // bgr
        int sz = mp.size();
        double angle;
        for(int i = 0; i < sz; i++){
            for(int j = 0; j < sz; j++){
                if(i != j){
                    cnt = 2;
                    ArrayList<MyPoint> temp = new ArrayList<>();
                    temp.add(mp.get(i));
                    temp.add(mp.get(j));
                    for(int k = 0; k < sz; k++){
                        if(i != k || j != k){
                            angle = calculateAngle(mp.get(i), mp.get(j), mp.get(k));
                            if(angle+0.00001 < thres){
                                cnt++;
                                temp.add(mp.get(k));
                            }
                        }
                    }
                    if(cnt > mx){
                        mx = cnt;
                        mxind = ll.size();
                        ll.add(temp);
                    }
                }
            }
        }
//        System.out.println("mx ind: "+mxind+" cnt; "+ mx+" lines1 sz: "+lines1.size());
        if(ll.size() > mxind && ll.get(mxind).size() >= 4){
            for(int i = 0; i < ll.get(mxind).size(); i++){
                //System.out.println("->> "+lines.get(mxind).get(i).r+"     "+lines.get(mxind).get(i).c);
                outputImage.put(ll.get(mxind).get(i).r, ll.get(mxind).get(i).c, d);
                outputImage.put(ll.get(mxind).get(i).r, ll.get(mxind).get(i).c-1, d);
                outputImage.put(ll.get(mxind).get(i).r, ll.get(mxind).get(i).c-2, d);
                outputImage.put(ll.get(mxind).get(i).r, ll.get(mxind).get(i).c+1, d);
                outputImage.put(ll.get(mxind).get(i).r, ll.get(mxind).get(i).c+2, d);
                outputImage.put(ll.get(mxind).get(i).r-1, ll.get(mxind).get(i).c, d);
                outputImage.put(ll.get(mxind).get(i).r-2, ll.get(mxind).get(i).c, d);
                outputImage.put(ll.get(mxind).get(i).r+1, ll.get(mxind).get(i).c, d);
                outputImage.put(ll.get(mxind).get(i).r+2, ll.get(mxind).get(i).c, d);
            }
        }
        
        return ll.get(mxind);
        
    }
    
    private double calculateAngle(MyPoint a, MyPoint b, MyPoint c){
        MyPoint ab = new MyPoint(b.r - a.r, b.c - a.c);
        MyPoint ac = new MyPoint(c.r - a.r, c.c - a.c);
        double abac = ab.r * ac.r + ab.c * ac.c;
        double mab = Math.sqrt(ab.c * ab.c + ab.r * ab.r );
        double mac = Math.sqrt(ac.c * ac.c + ac.r * ac.r );
        double frac = abac/(mab * mac);
        double angl = Math.toDegrees(Math.acos(frac));
//        double angl = Math.acos(frac);
        //System.out.println("rad: "+Math.acos(frac)+" deg: "+angl);
        return  angl;
    }
    
    private void drawDirection(){
        double minDist = Double.MAX_VALUE;
        MyPoint closestP = new MyPoint(0, 0);
        MyPoint initP = new MyPoint(frame.rows() - 1, myPos);
        for(MyPoint mp : bTWSelectedPix){
            if(minDist > distCalc(initP, mp)){
                closestP.r = mp.r;
                closestP.c = mp.c;
            }
        }
        for(MyPoint mp : wTBSelectedPix){
            if(minDist < distCalc(initP, mp)){
                closestP.r = mp.r;
                closestP.c = mp.c;
            }
        }
        prevP.r = initP.r;
        prevP.c = initP.c;

        estimateAndDraw(closestP);
    }
    
//    private void drawDir(){
//        int sd = 0;
//        double minDist = Double.MAX_VALUE;
//        MyPoint closestP = new MyPoint(0, 0);
//        MyPoint initP = new MyPoint(frame.rows() - 1, myPos);
//        for(MyPoint mp : bTWSelectedPix){
//            if(minDist > distCalc(initP, mp)){
//                closestP.r = mp.r;
//                closestP.c = mp.c;
//            }
//        }
//        for(MyPoint mp : wTBSelectedPix){
//            if(minDist < distCalc(initP, mp)){
//                closestP.r = mp.r;
//                closestP.c = mp.c;
//                sd = 1;
//            }
//        }
//        MyPoint secondClosestP = new MyPoint(0, 0);
//        if(sd == 0){
//            for(int i = closestP.c - 115; i < closestP.c; i++){
//                double[] prc = frame.get(closestP.r, i-1);
//                double[] crc = frame.get(closestP.r, i);
////                System.out.println("0 prc: "+prc[0]+" crc: "+crc[0]);
//                //outputImage.put(closestP.r, i, new double[]{0, 255, 0});
//                if(prc[0] == 255 && crc[0] == 0){
//                    secondClosestP.r = closestP.r;
//                    secondClosestP.c = i;
//                    break;
//                }
//            }
//        }
//        else{
//             for(int i = closestP.c; i < closestP.c + 185; i++){
//                double[] prc = frame.get(closestP.r, i-1);
//                double[] crc = frame.get(closestP.r, i);
////                 System.out.println("prc: "+prc[0]+" crc: "+crc[0]);
//                if(prc[0] == 0 && crc[0] == 255){
//                    secondClosestP.r = closestP.r;
//                    secondClosestP.c = i;
//                    break;
//                }
//            }           
//        }
//        
//        //System.out.println("c: "+secondClosestP.c+" r: "+secondClosestP.r);
//        
//        closestP.c = (closestP.c + secondClosestP.c)/2;
//        Imgproc.line(outputImage, new Point(initP.c, initP.r), new Point(closestP.c, closestP.r), new Scalar(new double[]{0, 255, 0}));
//        prevP.r = closestP.r;
//        prevP.c = closestP.c;
////        for(int i = -3; i < 7; i++){
////            outputImage.put(closestP.r, closestP.c + i, new double[]{0, 255, 0});
////        }
//    }
    
    private double distCalc(MyPoint x, MyPoint y){
        return Math.sqrt((x.r - y.r) * (x.r - y.r) + (x.c - y.c) * (x.c - y.c));
    }
    
    int changeColor = 1;
    private void estimateAndDraw(MyPoint P){
        double minDist = Double.MAX_VALUE;
        double dist = 0;
        MyPoint closestP = new MyPoint(0, 0);
        MyPoint secClosestP = new MyPoint(0, 0);
        for(MyPoint mp : bTWSelectedPix){
            dist = distCalc(P, mp);
//            System.out.println("mpcr "+mp.r +" mpcc "+ mp.c +"  Pr: "+ P.r +" Pc: "+P.c+" dist: "+distCalc(P, mp));
            if(minDist > dist ){
                minDist = dist;
                closestP.r = mp.r;
                closestP.c = mp.c;
            }
        }
        minDist = Double.MAX_VALUE;
        for(MyPoint mp : wTBSelectedPix){
            dist = distCalc(closestP, mp);
            if(minDist > dist){
                minDist = dist;
                secClosestP.r = mp.r;
                secClosestP.c = mp.c;
            }
        }
        
        //System.out.println("cr "+closestP.r +" cc "+ closestP.c +"  scr: "+ secClosestP.r +" scc: "+secClosestP.c);        
        closestP.r = P.r;
        closestP.c = (closestP.c + secClosestP.c)/2;
        if(changeColor == 1){
            Imgproc.line(outputImage, new Point(prevP.c, prevP.r), new Point(closestP.c, P.r), new Scalar(new double[]{0, 255, 0}));        
            changeColor = 2;
        }
        else if(changeColor == 2){
            Imgproc.line(outputImage, new Point(prevP.c, prevP.r), new Point(closestP.c, P.r), new Scalar(new double[]{215, 232, 135}));        
            changeColor = 3;
        }
        else{
            Imgproc.line(outputImage, new Point(prevP.c, prevP.r), new Point(closestP.c, P.r), new Scalar(new double[]{0, 255, 255}));        
            changeColor = 1;
        }
        prevP.r = closestP.r;
        prevP.c = closestP.c;
    }
    
    
}

