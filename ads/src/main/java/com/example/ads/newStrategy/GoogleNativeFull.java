package com.example.ads.newStrategy;//package com.vyroai.autocutcut.ads.max;
//

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.nativead.NativeAd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

public class GoogleNativeFull {

    final private int totalLevels = 4;
    private ArrayList<ArrayList<Object>> adUnits;

    private final String adUnitId = "ca-app-pub-9507635869843997/8657863145";

    public GoogleNativeFull(Context context) {
        instantiateList();
        loadnativead(context);
    }

    private void instantiateList() {
        adUnits = new ArrayList<>();

        adUnits.add(0, new ArrayList<Object>(Arrays.asList(adUnitId, new Stack<AppOpenAd>())));
        adUnits.add(1, new ArrayList<Object>(Arrays.asList(adUnitId, new Stack<AppOpenAd>())));
        adUnits.add(2, new ArrayList<Object>(Arrays.asList(adUnitId, new Stack<AppOpenAd>())));
        adUnits.add(3, new ArrayList<Object>(Arrays.asList(adUnitId, new Stack<AppOpenAd>())));
        adUnits.add(4, new ArrayList<Object>(Arrays.asList(adUnitId, new Stack<AppOpenAd>())));
    }

    public void loadnativead(Context context) {
        NativeAdLoad(context, totalLevels);
    }


    public NativeAd getDefaultAd(Context activity) {
        for (int i = totalLevels; i >= 0; i--) {

            ArrayList<Object> list = adUnits.get(i);
            String adunitid = (String) list.get(0);
            Stack<NativeAd> stack = (Stack<NativeAd>) list.get(1);

            NativeAdLoadSpecific(activity, adunitid, stack);

            if (stack != null && !stack.isEmpty()) {
                return stack.pop();
            }
        }

        return null;
    }

    public void NativeAdLoad(Context activity, int level) {
        if (level < 0) {
            return;
        }

        if (adUnits.size() < level) {
            return;
        }

        ArrayList<Object> list = adUnits.get(level);
        String adunitid = (String) list.get(0);
        Stack<NativeAd> stack = (Stack<NativeAd>) list.get(1);

        final AdLoader adLoader = new AdLoader.Builder(activity, adunitid)
                .forNativeAd(new NativeAd.OnNativeAdLoadedListener() {
                    @Override
                    public void onNativeAdLoaded(NativeAd ad) {
                        stack.push(ad);
                    }
                }).withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        // Handle the failure by logging, altering the UI, and so on.
                        NativeAdLoad(activity, level - 1);
                    }
                })
                .build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    public void NativeAdLoadSpecific(Context activity, String adUnitId, Stack<NativeAd> stack) {
        final AdLoader adLoader = new AdLoader.Builder(activity, adUnitId)
                .forNativeAd(new NativeAd.OnNativeAdLoadedListener() {
                    @Override
                    public void onNativeAdLoaded(NativeAd ad) {
                        stack.push(ad);
                    }
                }).build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }
}
