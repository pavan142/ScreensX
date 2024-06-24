package com.frankenstein.screenx.models;


import android.graphics.drawable.Drawable;

import androidx.lifecycle.MutableLiveData;

public class OnboardingDetail {
    public String name;
    public String title1;
    public String title2;
    public Drawable drawable;
    public String description;

    public OnboardingDetail(String name, String title1, String title2, Drawable drawable, String description) {
        this.name = name;
        this.title1 = title1;
        this.title2 = title2;
        this.drawable = drawable;
        this.description = description;
    }
}
