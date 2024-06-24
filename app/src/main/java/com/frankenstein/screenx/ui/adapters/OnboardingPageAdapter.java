package com.frankenstein.screenx.ui.adapters;

import android.content.Context;
import android.os.Bundle;

import com.frankenstein.screenx.R;
import com.frankenstein.screenx.helper.Logger;
import com.frankenstein.screenx.models.OnboardingDetail;
import com.frankenstein.screenx.ui.OnboardingDetailFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class OnboardingPageAdapter extends FragmentStateAdapter {

    private Logger _logger = Logger.getInstance("OnboardingPageAdapter");
    public static final Map<String, OnboardingDetail> detailsMap = new HashMap<>();
    private final ArrayList<String> _detailOrder = new ArrayList<>();
    public static  final String THEPROBLEM_PAGE = "THEPROBLEM_PAGE";
    public static  final String SEARCH_PAGE = "SEARCH_PAGE";
    public static final String ORGANIZE_PAGE = "ORGANIZE_PAGE";

    public OnboardingPageAdapter(FragmentActivity fa, Context context) {
        super(fa);
        detailsMap.put(THEPROBLEM_PAGE,new OnboardingDetail(
                THEPROBLEM_PAGE,
                context.getResources().getString(R.string.onboarding_theproblem_title1),
                context.getResources().getString(R.string.onboarding_theproblem_title2),
                context.getResources().getDrawable(R.drawable.ic_woman_on_photo),
                context.getResources().getString(R.string.onboarding_theproblem_description)));
        detailsMap.put(SEARCH_PAGE,new OnboardingDetail(
                SEARCH_PAGE,
                context.getResources().getString(R.string.onboarding_search_title1),
                context.getResources().getString(R.string.onboarding_search_title2),
                context.getResources().getDrawable(R.drawable.ic_woman_with_binoculars),
                context.getResources().getString(R.string.onboarding_search_description)));
        detailsMap.put(ORGANIZE_PAGE,new OnboardingDetail(
                ORGANIZE_PAGE,
                context.getResources().getString(R.string.onboarding_organize_title1),
                context.getResources().getString(R.string.onboarding_organize_title2),
                context.getResources().getDrawable(R.drawable.ic_man_with_folders),
                context.getResources().getString(R.string.onboarding_organize_description)
        ));
        _detailOrder.add(THEPROBLEM_PAGE);
        _detailOrder.add(SEARCH_PAGE);
        _detailOrder.add(ORGANIZE_PAGE);
    }

    @Override
    public int getItemCount() {
        return _detailOrder.size();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        _logger.log("View Pager Instantiating item", position);
        String detailName = _detailOrder.get(position);
        OnboardingDetailFragment fragment =  new OnboardingDetailFragment();
        Bundle bundle = new Bundle();
        bundle.putString(OnboardingDetailFragment.ONBOARDING_DETAIL_NAME, detailName);
        fragment.setArguments(bundle);
        return fragment;
    }
}
