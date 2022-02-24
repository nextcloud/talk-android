/*
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * @author Marcel Hibbe
 * Copyright (C) 2020 Tobias Kaminsky
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
 * Copyright (C) 2020 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.nextcloud.talk.R;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

/**
 * A Drawable object that draws a status
 */
public class StatusDrawable extends Drawable {
    private String text;
    private @DrawableRes int icon = -1;
    private Paint textPaint;
    private int backgroundColor;
    private final float radius;
    private Context context;

    public StatusDrawable(String status, String statusIcon, float statusSize, int backgroundColor, Context context) {
        radius = statusSize;
        this.backgroundColor = backgroundColor;


        if ("dnd".equals(status)) {
            icon = R.drawable.ic_user_status_dnd;
            this.context = context;
        } else if (TextUtils.isEmpty(statusIcon)) {
            switch (status) {
                case "online":
                    icon = R.drawable.online_status;
                    this.context = context;
                    break;

                case "away":
                    icon = R.drawable.ic_user_status_away;
                    this.context = context;
                    break;

                default:
                    // do not show
                    break;
            }
        } else {
            text = statusIcon;

            textPaint = new Paint();
            textPaint.setTextSize(statusSize);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
    }

    /**
     * Draw in its bounds (set via setBounds) respecting optional effects such as alpha (set via setAlpha) and color
     * filter (set via setColorFilter) a circular background with a user's first character.
     *
     * @param canvas The canvas to draw into
     */
    @Override
    public void draw(@NonNull Canvas canvas) {
        if (text != null) {
            textPaint.setTextSize(1.6f * radius);
            canvas.drawText(text, radius, radius - ((textPaint.descent() + textPaint.ascent()) / 2), textPaint);
        }

        if (icon != -1) {

            Paint backgroundPaint = new Paint();
            backgroundPaint.setStyle(Paint.Style.FILL);
            backgroundPaint.setAntiAlias(true);
            backgroundPaint.setColor(backgroundColor);

            canvas.drawCircle(radius, radius, radius, backgroundPaint);

            Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), icon, null);

            if (drawable != null) {
                drawable.setBounds(0,
                                   0,
                                   (int) (2 * radius),
                                   (int) (2 * radius));
                drawable.draw(canvas);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        textPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        textPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
