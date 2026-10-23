package com.thorautotranslate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

// This fixture runs as a separate APK, whose classpath does not contain the target app's Kotlin runtime.
public final class ScreenFixtureActivity extends Activity {
    private TextView dialogue;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dialogue = new TextView(this);
        dialogue.setText(line(getIntent()));
        dialogue.setTextSize(getIntent().getFloatExtra("font_size", 32f));
        dialogue.setGravity(Gravity.CENTER);
        dialogue.setTextColor(Color.WHITE);
        dialogue.setBackgroundColor(Color.rgb(95, 65, 38));
        setContentView(dialogue);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        dialogue.setText(line(intent));
        dialogue.setTextSize(intent.getFloatExtra("font_size", 32f));
    }
    private String line(Intent intent) {
        String text = intent.getStringExtra("line");
        return text == null ? "The gate is locked." : text;
    }
}
