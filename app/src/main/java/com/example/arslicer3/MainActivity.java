package com.example.arslicer3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.ImageFormat;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.PlaneRenderer;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ArFragment arFragment;
    private ModelRenderable shapeRenderable;

    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment1);
        imageView = findViewById(R.id.capturedImage);

        // build renderable
        ModelRenderable.builder()
                .setSource(this, Uri.parse("80mmRing.sfb"))
                .build()
                .thenAccept(renderable -> {
                    shapeRenderable = renderable;
                    shapeRenderable.setShadowCaster(false);
                    shapeRenderable.setShadowReceiver(false);
                })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load shape renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });


        // change plane colour
//        arFragment.getArSceneView()
//            .getPlaneRenderer()
//            .getMaterial()
//            .thenAccept(material -> material.setFloat3(PlaneRenderer.MATERIAL_COLOR, new Color(0.0f, 0.0f, 1.0f, 1.0f)) );

        // build texture sampler
        Texture.Sampler sampler = Texture.Sampler.builder()
                .setMinFilter(Texture.Sampler.MinFilter.LINEAR)
                .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
                .setWrapMode(Texture.Sampler.WrapMode.REPEAT).build();

        // build texture with sampler
        CompletableFuture<Texture> trigrid = Texture.builder()
                .setSource(this, R.drawable.checkered_texture_red)
                .setSampler(sampler).build();

        // set plane texture
        arFragment.getArSceneView()
                .getPlaneRenderer()
                .getMaterial()
                .thenAcceptBoth(trigrid, (material, texture) -> {
                    material.setTexture(PlaneRenderer.MATERIAL_TEXTURE, texture);
                });


        // setup listener
        arFragment.setOnTapArPlaneListener(
            (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                if (shapeRenderable == null) {
                    return;
                }

                // 1. on tap get camera image

                // check session
                if (arFragment.getArSceneView().getSession() == null)
                    return; // no session - ignore the tap

                // check frame
                Frame frame = arFragment.getArSceneView().getArFrame();
                if (frame == null)
                    return; // no frame - ignore the tap

                // copy the camera stream to a bitmap
                Bitmap grayscaleBitmap;
                try (Image image = frame.acquireCameraImage()) {
                    if (image.getFormat() != ImageFormat.YUV_420_888) {
                        throw new IllegalArgumentException(
                                "Expected image in YUV_420_888 format, got format " + image.getFormat());
                    }

                    // get image
                    ByteBuffer buffer  = image.getPlanes()[0].getBuffer();

                    Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                            Bitmap.Config.ALPHA_8);
                    bitmap.copyPixelsFromBuffer(buffer);

                    // rotate image 90deg clockwise
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                    // invert greyscale
                    grayscaleBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(grayscaleBitmap);
                    Paint paint = new Paint();

                    ColorMatrix matrixInvert = new ColorMatrix();
                    matrixInvert.set(new float[]
//                            {
//                                    1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
//                                    0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
//                                    0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
//                                    0.0f, 0.0f, 0.0f, -1.0f, 255.0f
//                            });
                            {
                                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                                        0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                                        0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                                        0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                            });

                    ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrixInvert);
                    paint.setColorFilter(filter);

                    canvas.drawBitmap(bitmap, 0, 0, paint);

//                    imageView.setImageBitmap(grayscaleBitmap);

                    // TODO 2. find borders -- or make everything black/white with threshold and then find borders
                    Bitmap bwBitmap = Bitmap.createBitmap(grayscaleBitmap.getWidth(), grayscaleBitmap.getHeight(), Bitmap.Config.RGB_565);
                    canvas = new Canvas(bwBitmap);
                    //set contrast
                    ColorMatrix contrastMatrix = new ColorMatrix();
                    //change contrast
                    float contrast = 50.f;
                    float shift = (-.5f * contrast + .5f) * 255.f;
                    contrastMatrix .set(new float[] {
                            contrast , 0, 0, 0, shift ,
                            0, contrast , 0, 0, shift ,
                            0, 0, contrast , 0, shift ,
                            0, 0, 0, 1, 0 });
                    //apply contrast
                    Paint contrastPaint = new Paint();
                    contrastPaint.setColorFilter(new ColorMatrixColorFilter(contrastMatrix ));
                    canvas.drawBitmap(grayscaleBitmap, 0, 0, contrastPaint);

                    //set saturation
                    ColorMatrix saturationMatrix = new ColorMatrix();
                    saturationMatrix.setSaturation(0); // set color saturation to 0 for b/w
                    //apply new saturation
                    Paint saturationPaint = new Paint();
                    saturationPaint.setColorFilter(new ColorMatrixColorFilter(saturationMatrix));
                    canvas.drawBitmap(bwBitmap, 0, 0, saturationPaint);


                    imageView.setImageBitmap(bwBitmap); // bitmap is 640x480 (Height x Width); only vertical is shown fully, horizontal is extended on bitmap


                    // check center, and 4 edges (N E S W)

//                    Log.e(TAG, "Bitmap width: " + bwBitmap.getWidth() + " bmap height " + bwBitmap.getHeight() + "");
//                    Log.e(TAG, "HitResult distance: " + hitResult.getDistance() + "");
                    // 42cm real is 31.47cm AR
                    // 22cm real is 14.19cm AR
                    // 62cm real is 50.00cm AR
                    // AR = f(real) = 0.864*real - 4.818
                    // based on 22cm, and 42cm; for 62cm there is less an error of 2%

//                    Log.e(TAG, "Colour?: " + bwBitmap.getPixel(240, 320));
                    // Color.BLACK and COLOR.WHITE are good good



                    // maybe put it in the try catch earlier because sometimes ARcore isn't ready for taking a picture -.-
                    // set anchor where you touched
                    // create the anchor
//                Anchor anchor = hitResult.createAnchor();

                    // create array of anchors
                    List<Anchor> anchors = new ArrayList<>();
                    // create array of anchor nodes
                    List<AnchorNode> anchorNodes = new ArrayList<>();
                    // create array of transformable nodes
                    List<TransformableNode> transformableNodes = new ArrayList<>();

                    // get minimal distances without intersections in 4 directions
                    int dirPowerBtn = 150;
                    int dirNothaang = 150;
                    int dirFrontCam = 150;
                    int dirCharging = 150;

                    float minDistance = 0.003f;


                    for (int i = 0; i < 160; ++i)
                    {
                        dirPowerBtn += 8;   // increment by 2px on bwBitmap

                        Pose startPose = frame.hitTest(540.0f, 1170.0f).get(0).createAnchor().getPose();
                        Pose endPose = frame.hitTest(540.0f + dirPowerBtn, 1170.0f).get(0).createAnchor().getPose();

                        float dx = startPose.tx() - endPose.tx();
//                        float dy = anchors.get(0).getPose().ty() - endPose.ty();
                        float dz = startPose.tz() - endPose.tz();

//                        float distanceMeters = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                        float distanceMeters = (float) Math.sqrt(dx*dx + dz*dz);
                        Log.e(TAG, "Distance: " + distanceMeters);
                        if (distanceMeters > minDistance) {
                            break;
                        }

                    }

                    for (int i = 0; i < 160; ++i)
                    {
                        dirNothaang += 8;   // increment by 2px on bwBitmap

                        Pose startPose = frame.hitTest(540.0f, 1170.0f).get(0).createAnchor().getPose();
                        Pose endPose = frame.hitTest(540.0f - dirNothaang, 1170.0f).get(0).createAnchor().getPose();

                        float dx = startPose.tx() - endPose.tx();
//                        float dy = anchors.get(0).getPose().ty() - endPose.ty();
                        float dz = startPose.tz() - endPose.tz();

//                        float distanceMeters = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                        float distanceMeters = (float) Math.sqrt(dx*dx + dz*dz);

                        Log.e(TAG, "Distance: " + distanceMeters);
                        if (distanceMeters > minDistance) {
                            break;
                        }

                    }

                    for (int i = 0; i < 160; ++i)
                    {
                        dirCharging += 8;   // increment by 2px on bwBitmap

                        Pose startPose = frame.hitTest(540.0f, 1170.0f).get(0).createAnchor().getPose();
                        Pose endPose = frame.hitTest(540.0f, 1170.0f + dirCharging).get(0).createAnchor().getPose();

                        float dx = startPose.tx() - endPose.tx();
//                        float dy = anchors.get(0).getPose().ty() - endPose.ty();
                        float dz = startPose.tz() - endPose.tz();

//                        float distanceMeters = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                        float distanceMeters = (float) Math.sqrt(dx*dx + dz*dz);

                        Log.e(TAG, "Distance: " + distanceMeters);
                        if (distanceMeters > minDistance) {
                            break;
                        }

                    }

                    for (int i = 0; i < 160; ++i)
                    {
                        dirFrontCam += 8;   // increment by 2px on bwBitmap

                        Pose startPose = frame.hitTest(540.0f, 1170.0f).get(0).createAnchor().getPose();
                        Pose endPose = frame.hitTest(540.0f, 1170.0f - dirFrontCam).get(0).createAnchor().getPose();

                        float dx = startPose.tx() - endPose.tx();
//                        float dy = anchors.get(0).getPose().ty() - endPose.ty();
                        float dz = startPose.tz() - endPose.tz();

//                        float distanceMeters = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                        float distanceMeters = (float) Math.sqrt(dx*dx + dz*dz);

                        Log.e(TAG, "Distance: " + distanceMeters);
                        if (distanceMeters > minDistance) {
                            break;
                        }

                    }


                    List<Pair<Float, Float>> points = getCenters(dirPowerBtn, dirNothaang, dirCharging, dirFrontCam, bwBitmap);

//                    anchors.add(frame.hitTest(540.0f, 1170.0f).get(0).createAnchor());   // middle of the camera
                    for (int i = 0; i < points.size(); ++i)
                    {
                        anchors.add(frame.hitTest(points.get(i).first, points.get(i).second).get(0).createAnchor());   // middle of the camera
                        anchorNodes.add(new AnchorNode(anchors.get(i)));
                        anchorNodes.get(i).setParent(arFragment.getArSceneView().getScene());
                        transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
                        transformableNodes.get(i).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
                        transformableNodes.get(i).setParent(anchorNodes.get(i));
                        transformableNodes.get(i).setRenderable(shapeRenderable);

                    }


//                    anchors.add(frame.hitTest(540.0f + dirPowerBtn, 1170.0f).get(0).createAnchor());   // middle of the camera
//                    anchorNodes.add(new AnchorNode(anchors.get(1)));
//                    anchorNodes.get(1).setParent(arFragment.getArSceneView().getScene());
//                    transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
//                    transformableNodes.get(1).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
//                    transformableNodes.get(1).setParent(anchorNodes.get(1));
//                    transformableNodes.get(1).setRenderable(shapeRenderable);
//
//                    anchors.add(frame.hitTest(540.0f - dirNothaang, 1170.0f).get(0).createAnchor());   // middle of the camera
//                    anchorNodes.add(new AnchorNode(anchors.get(2)));
//                    anchorNodes.get(2).setParent(arFragment.getArSceneView().getScene());
//                    transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
//                    transformableNodes.get(2).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
//                    transformableNodes.get(2).setParent(anchorNodes.get(2));
//                    transformableNodes.get(2).setRenderable(shapeRenderable);
//
//                    anchors.add(frame.hitTest(540.0f, 1170.0f + dirCharging).get(0).createAnchor());   // middle of the camera
//                    anchorNodes.add(new AnchorNode(anchors.get(3)));
//                    anchorNodes.get(3).setParent(arFragment.getArSceneView().getScene());
//                    transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
//                    transformableNodes.get(3).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
//                    transformableNodes.get(3).setParent(anchorNodes.get(3));
//                    transformableNodes.get(3).setRenderable(shapeRenderable);
//
//                    anchors.add(frame.hitTest(540.0f, 1170.0f - dirPowerBtn).get(0).createAnchor());   // middle of the camera
//                    anchorNodes.add(new AnchorNode(anchors.get(4)));
//                    anchorNodes.get(4).setParent(arFragment.getArSceneView().getScene());
//                    transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
//                    transformableNodes.get(4).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
//                    transformableNodes.get(4).setParent(anchorNodes.get(4));
//                    transformableNodes.get(4).setRenderable(shapeRenderable);

//                    // add anchor and print distance
//                    anchors.add(frame.hitTest(540.0f, 1170.0f).get(0).createAnchor());   // middle of the camera
//                    Log.e(TAG, "Distance: " + frame.hitTest(540.0f, 1170.0f).get(0).getDistance());
//
//                    // add anchor node to anchor
//                    anchorNodes.add(new AnchorNode(anchors.get(0)));
//                    anchorNodes.get(0).setParent(arFragment.getArSceneView().getScene());
//
//                    // add the transformable node
//                    transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
//                    // programmatically rotate the model -.-
//                    transformableNodes.get(0).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
//
//                    transformableNodes.get(0).setParent(anchorNodes.get(0));
//                    transformableNodes.get(0).setRenderable(shapeRenderable);


//                transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
//
//                transformableNodes.get(1).setLocalPosition(new Vector3(0.08f, 0f, 0f)); // 0.08m to the right
////                transformableNodes.get(1).setLocalPosition(new Vector3(0f, 0.08f, 0f)); // 0.08m up vertically
////                transformableNodes.get(1).setLocalPosition(new Vector3(0f, 0f, 0.08f)); // 0.08m down horizontally (in the direction of camera)
//                transformableNodes.get(1).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
//
//                transformableNodes.get(1).setParent(anchorNodes.get(0));
//                transformableNodes.get(1).setRenderable(shapeRenderable);

//                anchors.add(frame.hitTest(540.0f, 920.0f).get(0).createAnchor());   // -250px off - 40.72cm Height
//                anchors.add(frame.hitTest(540.0f, 1420.0f).get(0).createAnchor());   // +250px off - 42.20cm Height
//                anchors.add(frame.hitTest(290.0f, 1170.0f).get(0).createAnchor());   // -250px off - 45.00cm Height
//                    anchors.add(frame.hitTest(790.0f, 1170.0f).get(0).createAnchor());   // +250px off - 48.28cm Height
//
//                    // set anchor node where you touched
//                    anchorNodes.add(new AnchorNode(anchors.get(1)));
//                    anchorNodes.get(1).setParent(arFragment.getArSceneView().getScene());
//
//                    // create array of transformable nodes
//
//                    // set first transformable node where you touched
//                    // create the transformable node
//                    transformableNodes.add( new TransformableNode(arFragment.getTransformationSystem()));
//                    // programmatically rotate the model -.-
//                    transformableNodes.get(1).setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
//
//                    transformableNodes.get(1).setParent(anchorNodes.get(1));
//                    transformableNodes.get(1).setRenderable(shapeRenderable);
//
//                    Log.e(TAG, "Collision?: " + arFragment.getArSceneView().getScene().overlapTest(transformableNodes.get(1)));



                    // find another position for next node until you make 360deg
                    // switch relative node to next in array




                } catch (Exception e) {
                    Log.e(TAG, "Exception copying image", e);
                }





            });



    }

    // you can write write your own function that actually would optimize this circle packing problem
    private List<Pair<Float, Float>> getCenters (int dPB, int dNg, int dCP, int dFC, Bitmap bitmap)
    {
        // to return
        List<Pair<Float, Float>> points = new ArrayList<>();

        // conversion from main display px to bitmap px
        Integer dirPowerButtonSmall = (int) Math.floor(dPB * 4.0f / 9.0f);   // horizontal
        Integer dirNothingSmall = (int) Math.floor(dNg * 4.0f / 9.0f);   // horizontal
        Integer dirX = (int) Math.floor((dirPowerButtonSmall + dirNothingSmall) / 2.0f);

        Integer dirChargingPortSmall = (int) Math.floor(dCP * 32.0f / 117.0f);  // vertical
        Integer dirFrontCameraSmall = (int) Math.floor(dFC * 32.0f / 117.0f);   // vertical
        Integer dirY = (int) Math.floor((dirChargingPortSmall + dirFrontCameraSmall) / 2.0f);


        // working variables
        float x = 240.0f;    // horizontal
        float y = 320.0f;    // vertical
        Pair<Float, Float> root;    // root node

        while (bitmap.getPixel(Math.round(x), Math.round(y)) == -1){    // -1 == WHITE
            x += 2.0f;
        }
        x -= 2.0f;  // move back a bit
//        x -= (dirPowerButtonSmall / 2.0f);  // move it back by it's radius
        root = new Pair<>(x, y);
        // we've got a root node where x is maximal

        // create root row
        while (y > 0 && bitmap.getPixel(Math.round(x), Math.round(y)) == -1) {
            points.add(new Pair<>(x*9.0f/4.0f, y*117.0f/32.0f));    // do the conversion back to main display px
            y -= dirY;
        }
        y = 320;   // get back to root point
        while (y < bitmap.getHeight() && bitmap.getPixel(Math.round(x), Math.round(y)) == -1) {
            points.add(new Pair<>(x*9.0f/4.0f, y*117.0f/32.0f));    // do the conversion back to main display px
            y += dirY;
        }


        for (int i = 0; i < 6; ++i) {
            int sizeBeg = points.size();
            // now start with root
            x = root.first;
            y = root.second;
            // subtract sqrt(3) etc from x and y (check if it's white)
            x -= (dirX / 1.0f);
            y -= (dirY * Math.sqrt(3.0f) / 4.0f);

            if (x < 0)
                break;

            if (y < 0 || bitmap.getPixel(Math.round(x), Math.round(y)) != -1)
                y += (dirY * Math.sqrt(3.0f)/2.0f);

            if (Math.round(y) >= bitmap.getHeight() || Math.round(x) >= bitmap.getWidth() || Math.round(y) <= 0 || Math.round(x) <= 0)
                break;

            root = new Pair<>(x, y);
            while (y > 0 && bitmap.getPixel(Math.round(x), Math.round(y)) == -1) {
                points.add(new Pair<>(x * 9.0f / 4.0f, y * 117.0f / 32.0f));    // do the conversion back to main display px
                y -= dirY;
            }

            y = root.second;   // get back to root point
            if (Math.round(y) >= bitmap.getHeight() || Math.round(x) >= bitmap.getWidth() || Math.round(y) <= 0 || Math.round(x) <= 0)
                continue;
//            Log.e(TAG, "x " + x + " h " +bitmap.getWidth() );
//            Log.e(TAG, "y " + y + " h " +bitmap.getHeight() );
            while (y < bitmap.getHeight() && bitmap.getPixel(Math.round(x), Math.round(y)) == -1) {
                points.add(new Pair<>(x * 9.0f / 4.0f, y * 117.0f / 32.0f));    // do the conversion back to main display px
                y += dirY;
            }

//            if (sizeBeg == points.size())
//                break;  // no change whatsoever
            // start all over again
        }




        return points;
    }


}

