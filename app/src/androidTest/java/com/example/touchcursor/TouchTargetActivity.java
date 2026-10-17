package com.example.touchcursor;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Runs independently in the test APK, without the target APK's Kotlin runtime. */
public class TouchTargetActivity extends Activity {
    private int taps;
    private int holds;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        Button target = new Button(this);
        TextView touch = new TextView(this);
        touch.setContentDescription("fixture-touch");
        target.setAllCaps(false);
        target.setContentDescription("fixture-target");
        updateLabel(target);
        target.setOnClickListener(view -> { taps++; updateLabel(target); });
        target.setOnLongClickListener(view -> { holds++; updateLabel(target); return true; });
        target.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                touch.setText(event.getRawX() + "," + event.getRawY());
            return false;
        });
        layout.addView(target, new LinearLayout.LayoutParams(-1, 0, 1f));
        layout.addView(touch, new LinearLayout.LayoutParams(-1, -2));
        EditText field = new EditText(this);
        field.setContentDescription("fixture-edit");
        field.setText("alpha beta");
        field.setMinHeight((int) (80 * getResources().getDisplayMetrics().density));
        layout.addView(field, new LinearLayout.LayoutParams(-1, -2));
        setContentView(layout);
    }

    private void updateLabel(Button button) {
        button.setText("Taps: " + taps + "; holds: " + holds);
    }
}
