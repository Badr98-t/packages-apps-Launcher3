/*
 * Copyright (C) 2017 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.android.launcher3.compat.LauncherActivityInfoCompat;

public class CustomIconProvider extends IconProvider {

    private Context mContext;
    private IconsHandler mIconsHandler;

    public CustomIconProvider(Context context) {
        super();
        mContext = context;
        mIconsHandler = IconCache.getIconsHandler(context);
    }

    @Override
    public Drawable getIcon(LauncherActivityInfoCompat info, int iconDpi) {
        Drawable icon = Utilities.isRoundIconsPrefEnabled(mContext)? mIconsHandler.getRoundIcon(info.getApplicationInfo().packageName, iconDpi) : getIconFromHandler(info);
        return icon != null? icon : info.getIcon(iconDpi);
    }

    //get drawable icon for package
    private Drawable getIconFromHandler(LauncherActivityInfoCompat info) {
        Bitmap bm = mIconsHandler.getDrawableIconForPackage(info.getComponentName());
        if (bm == null) {
            return null;
        }
        return new BitmapDrawable(mContext.getResources(), Utilities.createIconBitmap(bm, mContext));
    }
}
