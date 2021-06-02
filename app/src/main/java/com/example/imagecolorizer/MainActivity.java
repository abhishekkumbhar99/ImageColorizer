package com.example.imagecolorizer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    ImageView imageView, result;
    Uri imageuri;
    Button colorise_button, select_button;
    private Bitmap bitmap;
    private Pix2pix model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.image);
        colorise_button = findViewById(R.id.colorise);
        result = findViewById(R.id.image2);
        select_button = findViewById(R.id.select);

        try {
            model = new Pix2pix(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        select_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), 12);
            }
        });

        colorise_button.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {

                Bitmap colorImageBitmap = model.colorize(bitmap);
                /*Log.d("Bitmap info", String.format("H:%d W:%d R:%d G:%d B:%d",
                        colorImageBitmap.getHeight(), colorImageBitmap.getWidth(),
                        Color.red(colorImageBitmap.getPixel(0, 0)),
                        Color.green(colorImageBitmap.getPixel(0, 0)),
                        Color.blue(colorImageBitmap.getPixel(0, 0))
                ));*/

                showResult(colorImageBitmap);
                try {
                    saveImageToExternal("output", colorImageBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });

    }

    private void showToast(CharSequence str) {
        Toast.makeText(getApplicationContext(),
                str,
                Toast.LENGTH_SHORT)
                .show();
    }

    private void showResult(Bitmap colorImage) {
        result.setImageBitmap(colorImage);
    }

    public void saveImageToExternal(String imgName, Bitmap bm) throws IOException {

        OutputStream out;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imgName + ".jpg");
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            out = resolver.openOutputStream(Objects.requireNonNull(imageUri));
        } else {
            String imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
            File imageFile = new File(imagesDir, imgName + ".jpg");
            out = new FileOutputStream(imageFile);
        }

        bm.compress(Bitmap.CompressFormat.JPEG, 100, out);
        Objects.requireNonNull(out).close();

        Log.d("Save Image", "Image saved to Pictures folder");
        showToast(String.format("Image saved in %s folder", Environment.DIRECTORY_PICTURES));

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 12 && resultCode == RESULT_OK && data != null) {
            imageuri = data.getData();
            String filename = imageuri.getPath();
            Log.d("Input File", filename);
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageuri);
                Bitmap bMapScaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true);
                imageView.setImageBitmap(bMapScaled);
                showToast("Image loaded");
                Log.d("Image", "Image Loaded");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
