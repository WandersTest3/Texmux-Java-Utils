package com.wanderstest3.termuxjavautils.termux;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TermuxResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int code = intent.getIntExtra("code", -1);
        TermuxBridge.deliver(code, intent.getBundleExtra("result"));
    }
}
