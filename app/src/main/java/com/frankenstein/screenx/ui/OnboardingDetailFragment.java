package com.frankenstein.screenx.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.frankenstein.screenx.R;
import com.frankenstein.screenx.helper.Logger;
import com.frankenstein.screenx.models.OnboardingDetail;
import com.frankenstein.screenx.ui.adapters.OnboardingPageAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class OnboardingDetailFragment extends Fragment {
    private OnboardingDetail _mDetail;
    private TextView _mTitle1;
    private TextView _mTitle2;
    private TextView _mDescription;
    private ImageView _mImage;
    private static final Logger _mLogger = Logger.getInstance("OnboardingDetailFragment");
    public static final String ONBOARDING_DETAIL_NAME = "ONBOARDING_DETAIL_NAME";

    public OnboardingDetailFragment () {
        super();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.homepage_onboarding_component, container, false);
        _mTitle1 = view.findViewById(R.id.title1);
        _mTitle2 = view.findViewById(R.id.title2);
        _mDescription = view.findViewById(R.id.description);
        _mImage = view.findViewById(R.id.image);
        Bundle bundle = getArguments();
        String permissionName = bundle.getString(ONBOARDING_DETAIL_NAME );
        _mDetail = OnboardingPageAdapter.detailsMap.get(permissionName);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        _mTitle1.setText(_mDetail.title1);
        _mTitle2.setText(_mDetail.title2);
        _mDescription.setText(_mDetail.description);
        _mImage.setImageDrawable(_mDetail.drawable);
        super.onViewCreated(view, savedInstanceState);
    }
}
