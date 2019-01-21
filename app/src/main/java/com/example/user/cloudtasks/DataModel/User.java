package com.example.user.cloudtasks.DataModel;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class User {

    private static User sUser;

    private List<String> mVideos;

    public List<String> getVideos() {
        return mVideos;
    }

    public void setVideos(List<String> mVideos) {
        this.mVideos = mVideos;
    }

    public static User getUser(Context context) {
        if(sUser == null){
            sUser = new User(context);
        }
        return sUser;
    }

    private User(Context context){
        mVideos = new ArrayList<>();
    }
}
