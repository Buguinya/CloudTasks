package com.example.user.cloudtasks;


import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;
import android.widget.Button;

import com.example.user.cloudtasks.DataModel.User;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UserTasks extends AppCompatActivity {

    private static final String SAVED_REFERENCES = "saved references";
    private Button mAddBtn;

    private List<String> mVideos;

    private File mTempVideo;

    private String mVideoUri = "";

    private VideoAdapter videoAdapter;

    private String mReferense;

    private StorageReference mStorageRef;

    private User mUserModel;

    private static final int REQUEST_CODE_PERMISSION_RECEIVE_CAMERA = 102;
    private static final int REQUEST_CODE_TAKE_VIDEO = 103;

    SharedPreferences sPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_tasks);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        mUserModel = User.getUser(UserTasks.this);

        mAddBtn = findViewById(R.id.add_btn);

        mAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addVideo();
            }
        });

        loadReferenceFromShardPreferences();
        mVideos = mUserModel.getVideos();


        videoAdapter = new VideoAdapter();
        videoAdapter.setVideos(mVideos);
        RecyclerView recyclerView = findViewById(R.id.vv_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(videoAdapter);

        mStorageRef = FirebaseStorage.getInstance().getReference();

        if (user != null) {
            mReferense = user.getUid();
        }
        downloadFileFromFirebaseStorage();
    }

    private void addVideo() {

        //Проверяем разрешение на работу с камерой
        boolean isCameraPermissionGranted = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        //Проверяем разрешение на работу с внешнем хранилещем телефона
        boolean isWritePermissionGranted = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        //Если разрешения != true
        if (!isCameraPermissionGranted || !isWritePermissionGranted) {

            String[] permissions;//Разрешения которые хотим запросить у пользователя

            if (!isCameraPermissionGranted && !isWritePermissionGranted) {
                permissions = new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
            } else if (!isCameraPermissionGranted) {
                permissions = new String[]{android.Manifest.permission.CAMERA};
            } else {
                permissions = new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
            }
            //Запрашиваем разрешения у пользователя
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSION_RECEIVE_CAMERA);
        } else {
            //Если все разрешения получены
            try {
                mTempVideo = createTempVideoFile(getExternalCacheDir());
            } catch (IOException e) {
                e.printStackTrace();
            }
            mVideoUri = mTempVideo.getAbsolutePath();

            List<Intent> intents = new ArrayList<>();
            Intent chooserIntent = null;

            Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

            takeVideoIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTempVideo));

            intents = addIntentsToList(this, intents, pickIntent);
            intents = addIntentsToList(this, intents, takeVideoIntent);


            if (!intents.isEmpty()) {
                chooserIntent = Intent.createChooser(intents.remove(intents.size() - 1), "Choose your image source");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Parcelable[]{}));
            }
            startActivityForResult(chooserIntent, REQUEST_CODE_TAKE_VIDEO);
        }
    }

    private String getRealPathFromURI(Uri uri) {
        String[] projection = {MediaStore.Video.Media.DATA};
        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int columnIndex = cursor
                .getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(columnIndex);
    }

    public static File createTempVideoFile(File storageDir) throws IOException {

        // Генерируем имя файла
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());//получаем время
        String videoFileName = "video_" + timeStamp;//состовляем имя файла

        //Создаём файл
        return File.createTempFile(
                videoFileName,  /* prefix */
                ".mp4",         /* suffix */
                storageDir      /* directory */
        );
    }

    public static List<Intent> addIntentsToList(Context context, List<Intent> list, Intent intent) {
        List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : resInfo) {
            String packageName = resolveInfo.activityInfo.packageName;
            Intent targetedIntent = new Intent(intent);
            targetedIntent.setPackage(packageName);
            list.add(targetedIntent);
        }
        return list;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_TAKE_VIDEO:
                if (resultCode == RESULT_OK) {
                    if (data != null && data.getData() != null) {
                        mVideoUri = getRealPathFromURI(data.getData());
                        Log.i("Load", "mVideoUri: " + mVideoUri + "UploadUri: " + data.getData());

                      /*  mVidView.setVideoPath(mVideoUri);

                        mVidView.setMediaController(new MediaController(this));
                        mVidView.requestFocus(0);
                        mVidView.start();*/
                        uploadFileInFireBaseStorage(data.getData());
                    } else if (mVideoUri != null) {
                        mVideoUri = Uri.fromFile(mTempVideo).toString();
                        Log.i("Load", "mVideoUri: " + mVideoUri);
                       /* mVidView.setVideoPath(mVideoUri);

                        mVidView.setMediaController(new MediaController(this));
                        mVidView.requestFocus(0);
                        mVidView.start();
                        uploadFileInFireBaseStorage(Uri.fromFile((mTempVideo)));*/
                        uploadFileInFireBaseStorage(Uri.fromFile(mTempVideo));

                    }
                }
                break;
        }
    }

     public void uploadFileInFireBaseStorage (Uri uri){
        final StorageReference ref = mStorageRef.child(mReferense).child("videos").child(uri.getEncodedPath());
        UploadTask uploadTask = ref.putFile(uri);
        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                double progress = (100.0 * taskSnapshot.getBytesTransferred());
                Log.i("Load","Upload is " + progress + "% done");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Uri downloadUri = taskSnapshot.getUploadSessionUri();
                Log.i("Load" , "Uri donwlod" + downloadUri);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i("Load", "onFailure: " + e);
            }
        });

         Task<Uri> uriTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
             @Override
             public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                 if (!task.isSuccessful()){
                     throw task.getException();
                 }
                 return ref.getDownloadUrl();
             }
         }).addOnCompleteListener(new OnCompleteListener<Uri>() {
             @Override
             public void onComplete(@NonNull Task<Uri> task) {
                 if (task.isSuccessful()){
                     Uri downloadUri = task.getResult();
                     addReferenceToSharedPreferense(downloadUri);
                     Log.i("Load", "onComplete: " + downloadUri);
                 }
             }
         });
         videoAdapter.setVideos(mVideos);
    }

    public void downloadFileFromFirebaseStorage(){
        File localFile = null;

        for (String video : mVideos) {
            try {
                localFile = createTempVideoFile(getExternalCacheDir());
                final File finalLocalFile = localFile;

                StorageReference myRef = FirebaseStorage.getInstance().getReferenceFromUrl(video);
                Log.i("Download", "myRef = " + myRef);
                myRef.getFile(localFile)
                        .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                mVideos.add(Uri.fromFile(finalLocalFile).toString());
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.i("Load", "" + e);
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static class VideoViewHolder extends RecyclerView.ViewHolder {

        private static Context context;
        VideoView mVidView;

        public VideoViewHolder(@NonNull View itemView){
            super(itemView);

            context = itemView.getContext();
            mVidView = itemView.findViewById(R.id.video_view);
        }

        public void bind(Uri uri){
            mVidView.setVideoURI(uri);
            mVidView.setMediaController(new MediaController(context));
            mVidView.requestFocus(0);
            mVidView.start();
        }
    }

    public class VideoAdapter extends RecyclerView.Adapter<VideoViewHolder>{

        List<String> mVideos = Collections.emptyList();

        public void setVideos(List<String> mVideos) {
            this.mVideos = mVideos;
        }

        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.video_item, viewGroup, false);
            return new VideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder videoViewHolder, int position) {
            videoViewHolder.bind(Uri.parse(mVideos.get(position)));
        }

        @Override
        public int getItemCount() {
            return mVideos.size();
        }
    }

    private void addReferenceToSharedPreferense(Uri uri){

        if (mVideos == null){
            mVideos = new ArrayList<>();
        }
        mVideos.add(uri.toString());
        Set<String> set = new HashSet<>(mVideos);

        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sPref.edit();
        editor.putStringSet(SAVED_REFERENCES, set);
        editor.apply();
    }


    private void loadReferenceFromShardPreferences(){
        sPref = getPreferences(MODE_PRIVATE);
        Set<String> set = sPref.getStringSet(SAVED_REFERENCES, null);
        if (set != null) {
            ArrayList<String> list = new ArrayList<>(set);
            mUserModel.setVideos(list);
        }

    }
}

