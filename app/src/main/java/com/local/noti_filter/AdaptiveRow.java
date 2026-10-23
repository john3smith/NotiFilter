package com.local.noti_filter;

import android.content.Context;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keep action labels intact; stack controls when their natural text widths do not fit. */
final class AdaptiveRow extends LinearLayout {
    private final boolean equalWidth;
    private final int gap;

    public AdaptiveRow(Context context) { this(context, false); }

    AdaptiveRow(Context context, boolean equalWidth) {
        super(context);
        this.equalWidth = equalWidth;
        gap = Math.round(8 * getResources().getDisplayMetrics().density);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int total = 0, widest = 0, count = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int width;
            if (child instanceof TextView) {
                TextView text = (TextView) child;
                width = Math.max(text.getMinimumWidth(), (int) Math.ceil(Layout.getDesiredWidth(
                        text.getText(), text.getPaint())) + text.getCompoundPaddingLeft() + text.getCompoundPaddingRight());
            } else {
                child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                width = child.getMeasuredWidth();
            }
            widest = Math.max(widest, width);
            total += width;
            count++;
        }
        int required = (equalWidth ? widest * count : total) + Math.max(0, count - 1) * gap;
        boolean stacked = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && required > available;
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        int visible = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams p = (LayoutParams) child.getLayoutParams();
            p.height = LayoutParams.WRAP_CONTENT;
            p.width = stacked ? LayoutParams.MATCH_PARENT : equalWidth || visible == 0 ? 0 : LayoutParams.WRAP_CONTENT;
            p.weight = stacked ? 0 : equalWidth || visible == 0 ? 1 : 0;
            p.leftMargin = !stacked && visible > 0 ? gap : 0;
            p.topMargin = stacked && visible > 0 ? gap : 0;
            p.rightMargin = p.bottomMargin = 0;
            visible++;
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
