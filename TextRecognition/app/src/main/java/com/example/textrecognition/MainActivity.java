package com.example.textrecognition;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.text.BreakIterator;

public class MainActivity extends AppCompatActivity {

    //UI Views
    private MaterialButton inputImageBtn;
    private MaterialButton recognizedTextBtn;
    private ShapeableImageView imageIv;
    private EditText recognizedTextEt;

    private static final String TAG = "MAIN_TAG";

    private Uri imageUri = null;

    //Handle camera/gallery permission
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int STORAGE_REQUEST_CODE = 101;

    //arrays to ask permission to access the camera
    private String[] cameraPermissions;
    private String[] storagePermissions;

    //progress dialog
    private ProgressDialog progressDialog;

    //Text Recognizer
    private TextRecognizer textRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //init UI Views
        inputImageBtn = findViewById(R.id.inputImageBtn);
        recognizedTextBtn = findViewById(R.id.recognizedTextBtn);
        imageIv = findViewById(R.id.imageIv);
        recognizedTextEt = findViewById(R.id.recognizedTextEt);

        cameraPermissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        //init setup progress dialog, show when text from image is being processed
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false);

        //init TextRecognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        //handle click, show image dialog
        inputImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                showInputImageDialog();
            }
        });

        //Handle click, start recognizing image taken from the camera
        recognizedTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // check if image is picked or not, picked if imageUri is NOT null
                if(imageUri == null){
                     Toast.makeText(MainActivity.this, "Pick Image First...",Toast.LENGTH_SHORT).show();
                }
                else{
                    recognizeTextFromImage();
                }
            }
        });
    }

    private void recognizeTextFromImage() {
       Log.d(TAG, "recognizedTextFromImage: ");

        progressDialog.setMessage("Preparing image....");
        progressDialog.show();

        try {
            InputImage inputImage = InputImage.fromFilePath(this,imageUri);

            progressDialog.setMessage("Recognizing text...");

            Task<Text> textTaskResult = textRecognizer.process(inputImage)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text text) {

                            progressDialog.dismiss();
                            //get recognized text
                            String recognizedText =  text.getText();
                            Log.d(TAG, "onSuccess: recognizedText: " +recognizedText);
                            //set recognized text to edit text
                            recognizedTextEt.setText(recognizedText);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            //failed recognizing text from image, dismiss dialog, show reason in toast
                            progressDialog.dismiss();
                            Log.e(TAG, "onFailure: ",e);
                            Toast.makeText(MainActivity.this, "Failed in recognizing image due to"+e.getMessage(),Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            //Exception occurred while preparing InputImage, dismiss dialog, show reason in Toast
            progressDialog.dismiss();
            Log.e(TAG, "recognizedTextFromImage: ",e);
            Toast.makeText(this,"Failed in preparing image due to"+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    private void showInputImageDialog() {
        PopupMenu popupMenu = new PopupMenu(this,inputImageBtn);

        popupMenu.getMenu().add(Menu.NONE, 1,1,"CAMERA");
        popupMenu.getMenu().add(Menu.NONE, 2,2,"GALLERY");

        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menutItem) {

                int id = menutItem.getItemId();
                if(id == 1){
                    Log.d(TAG, "onMenuItemClick: Camera Clicked...");
                    if(checkCameraPermission()){
                        pickImageCamera();
                    }
                    else{
                         requestCameraPermission();
                    }

                }else if(id == 2){
                    Log.d(TAG, "onMenuItemClick: Gallery Clicked...");
                    if(checkStoragePermission()){
                        pickImageGallery();
                    }
                    else{
                        requestStoragePermission();
                    }
                }
                return true;
            }
        });
    }

    private void pickImageGallery(){
        Log.d(TAG, "pickImageGallery: ");
        //intention to pick image will show the selection of available images
        Intent intent = new Intent(Intent.ACTION_PICK);
        //set type of file we want to pick (the image)
        intent.setType("image/*");
        galleryActivityResultLauncher.launch(intent);
    }

    private ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                //get image here if picked
                    if(result.getResultCode() == Activity.RESULT_OK){
                        //image picked
                        Intent data = result.getData();
                        imageUri = data.getData();
                        Log.d(TAG, "onActivityResult: imageUri"+imageUri);
                        //set to imageview
                        imageIv.setImageURI(imageUri);

                    }else{
                        Log.d(TAG, "onActivityResult: cancelled");
                        //cancel
                        Toast.makeText(MainActivity.this, "Cancelled...",Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private void pickImageCamera(){
        Log.d(TAG, "pickImageCamera: ");
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE,"Sample Title");
        values.put(MediaStore.Images.Media.DESCRIPTION,"Sample Description");

        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraActivityLauncher.launch(intent);

        }
        private  ActivityResultLauncher<Intent> cameraActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {

                    @Override
                    public void onActivityResult(ActivityResult result) {
                        //get image if taken from camera
                        if(result.getResultCode() == Activity.RESULT_OK){
                            //image is taken from camera
                            //Image already stored in imageUri using pickImageCamera()
                            Log.d(TAG, "onActivityResult: imageUri"+ imageUri);
                            imageIv.setImageURI(imageUri);
                        }else{
                            Log.d(TAG, "onActivityResult: cancelled");
                            //cancelled
                            Toast.makeText(MainActivity.this, "Cancelled...",Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        private boolean checkStoragePermission(){
            boolean result = ContextCompat.checkSelfPermission(this , Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);

            return result;
        }

        private void requestStoragePermission(){
            ActivityCompat.requestPermissions(this,storagePermissions ,STORAGE_REQUEST_CODE);
    }

        private boolean checkCameraPermission(){
            //Checking Camera & storage writing permission, return value true if allowed / otherwise false
            boolean cameraResult  = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED);
            boolean storageResult  = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);

            return cameraResult && storageResult;
        }

        private void requestCameraPermission(){
            //Requesting the camera permission for camera intent
            ActivityCompat.requestPermissions(this,cameraPermissions,CAMERA_REQUEST_CODE);
        }

        //Handle permission result
        public void onRequestPermissionResult(int requestCode, @NonNull String[] permissions,@NonNull int[] grantResults){
                super.onRequestPermissionsResult(requestCode,permissions,grantResults);

                switch(requestCode){
                    case CAMERA_REQUEST_CODE:{
                        if (grantResults.length>0){

                            boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                            boolean storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                            if(cameraAccepted && storageAccepted){
                                pickImageCamera();
                            }
                            else{
                                Toast.makeText(this, "Camera & storage permission required", Toast.LENGTH_SHORT).show();
                            }
                        }
                        else{
                            //Cancelled Permission
                            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                    case STORAGE_REQUEST_CODE:{
                        if (grantResults.length>0){
                            //Check if storage permission is granted
                            boolean storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                            if(storageAccepted){
                                pickImageGallery();
                            }else{
                                Toast.makeText(this,"Storage permission required",Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    break;
                }
        }
}