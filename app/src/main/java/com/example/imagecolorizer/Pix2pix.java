package com.example.imagecolorizer;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Pix2pix {

    // Name of the model file (under assets folder)
    private final Activity activity;
    private static final String MODEL_PATH = "mobilenet_pix2pix_lab_fixed_fp16.tflite";
    private final Interpreter tflite;
    private final TensorBuffer grayscaleImage;
    private final TensorBuffer colorImage;
    private final Interpreter.Options options = new Interpreter.Options();

    public static final int IMG_SIZE_X = 256;   // height
    public static final int IMG_SIZE_Y = 256;   // width
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;
    // Input size
    private static final int BATCH_SIZE = 1;    // batch size
    private static final int CHANNEL = 1;    // 1 for gray scale & 3 for color images

    private final int[] imagePixels = new int[IMG_SIZE_X * IMG_SIZE_Y];
    private ByteBuffer inputImage;

    /**
     * holds a gpu delegate
     */
    GpuDelegate gpuDelegate;

    public Pix2pix(Activity activity) throws IOException {
        this.activity = activity;

//        // Initialize interpreter with GPU delegate
//        CompatibilityList compatList = new CompatibilityList();
//
//        if(compatList.isDelegateSupportedOnThisDevice()){
//            Log.d("Delegate","GPU Supported");
//            // if the device has a supported GPU, add the GPU delegate
//            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
//            Log.d("Delegate",delegateOptions.getClass().getName());
//            gpuDelegate = new GpuDelegate(delegateOptions);
//            options.addDelegate(gpuDelegate);
//        } else {
//            // if the GPU is not supported, run on 4 threads
//            options.setNumThreads(4);
//        }

        tflite = new Interpreter(loadModelFile(activity));

        // Creates input TensorBuffer object (Can load ByteBuffer)
        grayscaleImage = TensorBuffer.createFixedSize(
                new int[]{BATCH_SIZE, IMG_SIZE_X, IMG_SIZE_Y, CHANNEL}, DataType.FLOAT32);
        // Create output TensorBuffer object (Can get floatArray from it)
        colorImage = TensorBuffer.createFixedSize(new int[]{BATCH_SIZE, IMG_SIZE_X, IMG_SIZE_Y, 2},
                DataType.FLOAT32);

    }


    // Memory-map the model file in Assets
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Bitmap colorize(Bitmap bitmap) {
        preprocess(bitmap);
        long startTime = SystemClock.uptimeMillis();
        runInference();
        long endTime = SystemClock.uptimeMillis();
        Log.d("inference", "Timecost to run model inference: " + (endTime - startTime) + " ms");
        return postprocess();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void preprocess(Bitmap inputbitmap) {

        //resize input bitmap to 256x256
        Bitmap resized = Bitmap.createScaledBitmap(inputbitmap, IMG_SIZE_X, IMG_SIZE_Y, true);

        // To check input pixel values
        /*Log.d("Bitmap info", String.format("H:%d W:%d R:%d G:%d B:%d",
                resized.getHeight(), resized.getWidth(),
                Color.red(resized.getPixel(0, 0)),
                Color.green(resized.getPixel(0, 0)),
                Color.blue(resized.getPixel(0, 0))
        ));*/

        //convert bitmap to grayscale and store as ByteBuffer
        convertBitmapToByteBuffer(resized);
        Log.d("Preprocessing", "Image preprocessed.");
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {

        inputImage = ByteBuffer.allocateDirect(4 * BATCH_SIZE
                * IMG_SIZE_X
                * IMG_SIZE_Y
                * CHANNEL);
        inputImage.order(ByteOrder.nativeOrder());

        if (inputImage == null) {
            return;
        }
        inputImage.rewind();

        bitmap.getPixels(imagePixels, 0, bitmap.getWidth(), 0, 0,
                bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < IMG_SIZE_X; ++i) {
            for (int j = 0; j < IMG_SIZE_Y; ++j) {
                final int val = imagePixels[pixel++];
                float l_chan = convertToLab(val);
//                 Normalise to -1 to 1
                l_chan = l_chan / 50 - 1;
                inputImage.putFloat(l_chan);

                /*if (j % 128 == 0) {
                    Log.d("ByteBuffer L-channel", String.format("%f", l_chan));
                }*/
            }

        }
    }

    // This function converts 3-channel RGB/Gray pixels to 1-channel grayscale
    private float convertToGreyScale(int color) {
        float r = ((color >> 16) & 0xFF);
        float g = ((color >> 8) & 0xFF);
        float b = ((color) & 0xFF);

        int grayscaleValue = (int) (0.299f * r + 0.587f * g + 0.114f * b);
        return (grayscaleValue - IMAGE_MEAN) / IMAGE_STD;
    }

    // This function converts 3-channel RGB/Gray pixels to LAB and returns L-channel only
    // Range of L-channel: [0,100)
    private float convertToLab(int color) {
        int r = ((color >> 16) & 0xFF);
        int g = ((color >> 8) & 0xFF);
        int b = ((color) & 0xFF);

        double[] lab = new double[3];
        // Convert RGB pixel to LAB
        ColorUtils.RGBToLAB(r, g, b, lab);
        float L_channel = (float) lab[0];

        return L_channel;
    }

    private void runInference() {
        grayscaleImage.loadBuffer(inputImage);
        tflite.run(grayscaleImage.getBuffer(), colorImage.getBuffer());
        Log.d("Inference", "Done inference");
    }

    private void showToast(CharSequence str) {
        Toast.makeText(activity.getApplicationContext(), str,
                Toast.LENGTH_SHORT)
                .show();
    }

    private Bitmap postprocess() {

        float[] output_ab = colorImage.getFloatArray();
        float[] input_L = grayscaleImage.getFloatArray();

        int[] rgb_array = new int[IMG_SIZE_X * IMG_SIZE_Y * 3];

        int pixel = 0, k = 0, l = 0;
        for (int i = 0; i < IMG_SIZE_X; ++i) {
            for (int j = 0; j < IMG_SIZE_Y; ++j) {

                float l_pixel = input_L[l++];       //256*i + j           //a.getFloat();
                float a_pixel = output_ab[k++];     //256*i + 2*j + 0      //.getFloat();
                float b_pixel = output_ab[k++];     //256*i + 2*j + 1      //.getFloat();

                /*if (j % 128 == 0) {
                    Log.d("Values{l,a,b}:", String.format("%f, %f, %f", l_pixel, a_pixel, b_pixel));
                }*/

                l_pixel = (l_pixel + 1) * 50;
                a_pixel = a_pixel * 127;
                b_pixel = b_pixel * 127;

                int rgb_pixel = ColorUtils.LABToColor(l_pixel, a_pixel, b_pixel);
                int A = (rgb_pixel >> 24) & 0xff;
                int R = (rgb_pixel >> 16) & 0xff;
                int G = (rgb_pixel >> 8) & 0xff;
                int B = (rgb_pixel) & 0xff;
                rgb_array[pixel++] = R;
                rgb_array[pixel++] = G;
                rgb_array[pixel++] = B;

//              To check if pixel values are correct
                /*if (j % 128 == 0) {
                    Log.d("Values{A,R,G,B}:", String.format("%d, %d, %d, %d", A, R, G, B));
                }*/
            }

        }

        TensorImage outputRGBImage = new TensorImage(DataType.UINT8);
        outputRGBImage.load(rgb_array, new int[]{IMG_SIZE_X, IMG_SIZE_Y, 3});

        return outputRGBImage.getBitmap();
    }
}
