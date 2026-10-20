package com.example.touchcursor;

import android.app.Activity;
import android.content.ClipData;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Real framework drag/drop and scrolling targets, packaged only in the test APK. */
public class DragScrollTargetActivity extends Activity {
    private int holds;
    private int drops;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView status = new TextView(this);
        status.setContentDescription("fixture-drag-status");
        status.setText("holds=0; drops=0");
        layout.addView(status);
        TextView touch = new TextView(this);
        touch.setContentDescription("fixture-gesture");
        layout.addView(touch, new LinearLayout.LayoutParams(-1, (int) (40 * getResources().getDisplayMetrics().density)));
        LinearLayout targets = new LinearLayout(this);
        TextView source = new TextView(this);
        source.setText("Hold and drag this item");
        source.setContentDescription("fixture-target");
        source.setBackgroundColor(0xffaaccee);
        source.setOnTouchListener((view, event) -> {
            touch.setText("action=" + event.getActionMasked() + "; duration=" + (event.getEventTime() - event.getDownTime()));
            return false;
        });
        TextView destination = new TextView(this);
        destination.setText("Drop here");
        destination.setContentDescription("fixture-drop");
        destination.setBackgroundColor(0xffaaddaa);
        source.setOnLongClickListener(view -> {
            holds++;
            status.setText("holds=" + holds + "; drops=" + drops);
            return view.startDragAndDrop(ClipData.newPlainText("item", "item"), new View.DragShadowBuilder(view), null, 0);
        });
        destination.setOnDragListener((view, event) -> {
            if (event.getAction() == DragEvent.ACTION_DROP) {
                drops++;
                status.setText("holds=" + holds + "; drops=" + drops);
            }
            return true;
        });
        targets.addView(source, new LinearLayout.LayoutParams(0, -1, 1));
        targets.addView(destination, new LinearLayout.LayoutParams(0, -1, 1));
        layout.addView(targets, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView offset = new TextView(this);
        offset.setContentDescription("fixture-scroll-offset");
        offset.setText("0");
        layout.addView(offset);
        ScrollView scroll = new ScrollView(this);
        scroll.setContentDescription("fixture-scroll");
        TextView content = new TextView(this);
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 150; i++) rows.append("Scrollable row ").append(i).append('\n');
        content.setText(rows.toString());
        content.setTextSize(22);
        scroll.addView(content);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> offset.setText(Integer.toString(y)));
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
    }
}
