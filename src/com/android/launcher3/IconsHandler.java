package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.os.Build;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Inspired from http://stackoverflow.com/questions/31490630/how-to-load-icon-from-icon-pack
 */

public class IconsHandler {

    private static final String TAG = "IconsHandler";

    // map with available icons packs
    private HashMap<String, String> mIconPacks = new HashMap<>();

    // map with available drawable for an icons pack
    private Map<String, String> mPackagesDrawables = new HashMap<>();

    // instance of a resource object of an icon pack
    private Resources mIconPackres;

    // package name of the icons pack
    private String mIconPackPackageName;

    // list of back images available on an icons pack
    private List<Bitmap> mBackImages = new ArrayList<>();

    // bitmap mask of an icons pack
    private Bitmap mMaskImage = null;

    // front image of an icons pack
    private Bitmap mFrontImage = null;

    // scale factor of an icons pack
    private float mFactor = 1.0f;

    private PackageManager mPm;
    private Context mContext;

    public IconsHandler(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
        loadAvailableiconPacks();

        String iconPack = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getString(Utilities.KEY_ICON_PACK, "default");
        loadIconPack(iconPack);
    }

    /**
     * Parse icons pack metadata
     *
     * @param packageName Android package ID of the package to parse
     */
    public void loadIconPack(String packageName) {
        //clear icons pack
        mIconPackPackageName = packageName;
        mPackagesDrawables.clear();
        mBackImages.clear();
        clearCache();

        // system icons, nothing to do
        if (mIconPackPackageName.equalsIgnoreCase("default")) {
            return;
        }

        XmlPullParser xpp = null;

        try {
            // search appfilter.xml into icons pack apk resource folder
            mIconPackres = mPm.getResourcesForApplication(mIconPackPackageName);
            int appfilterid = mIconPackres.getIdentifier("appfilter", "xml", mIconPackPackageName);
            if (appfilterid > 0) {
                xpp = mIconPackres.getXml(appfilterid);
            }

            if (xpp != null) {
                int eventType = xpp.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        //parse <iconback> xml tags used as backgroud of generated icons
                        if (xpp.getName().equals("iconback")) {
                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                if (xpp.getAttributeName(i).startsWith("img")) {
                                    String drawableName = xpp.getAttributeValue(i);
                                    Bitmap iconback = loadBitmap(drawableName);
                                    if (iconback != null) {
                                        mBackImages.add(iconback);
                                    }
                                }
                            }
                        }
                        //parse <iconmask> xml tags used as mask of generated icons
                        else if (xpp.getName().equals("iconmask")) {
                            if (xpp.getAttributeCount() > 0 && xpp.getAttributeName(0).equals("img1")) {
                                String drawableName = xpp.getAttributeValue(0);
                                mMaskImage = loadBitmap(drawableName);
                            }
                        }
                        //parse <iconupon> xml tags used as front image of generated icons
                        else if (xpp.getName().equals("iconupon")) {
                            if (xpp.getAttributeCount() > 0 && xpp.getAttributeName(0).equals("img1")) {
                                String drawableName = xpp.getAttributeValue(0);
                                mFrontImage = loadBitmap(drawableName);
                            }
                        }
                        //parse <scale> xml tags used as scale factor of original bitmap icon
                        else if (xpp.getName().equals("scale")) {
                            if (xpp.getAttributeCount() > 0 && xpp.getAttributeName(0).equals("factor")) {
                                mFactor = Float.valueOf(xpp.getAttributeValue(0));
                            }
                        }
                        //parse <item> xml tags for custom icons
                        if (xpp.getName().equals("item")) {
                            String componentName = null;
                            String drawableName = null;

                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                if (xpp.getAttributeName(i).equals("component")) {
                                    componentName = xpp.getAttributeValue(i);
                                } else if (xpp.getAttributeName(i).equals("drawable")) {
                                    drawableName = xpp.getAttributeValue(i);
                                }
                            }
                            if (!mPackagesDrawables.containsKey(componentName)) {
                                mPackagesDrawables.put(componentName, drawableName);
                            }
                        }
                    }
                    eventType = xpp.next();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing appfilter.xml " + e);
        }

    }

    private Bitmap loadBitmap(String drawableName) {
        int id = mIconPackres.getIdentifier(drawableName, "drawable", mIconPackPackageName);
        if (id > 0) {
            Drawable bitmap = mIconPackres.getDrawable(id);
            if (bitmap instanceof BitmapDrawable) {
                return ((BitmapDrawable) bitmap).getBitmap();
            }
        }
        return null;
    }

    public Drawable getDefaultAppDrawable(ComponentName componentName) {
        try {
            return mPm.getActivityIcon(componentName);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Unable to found component " + componentName.toString() + e);
            return null;
        }
    }

    /**
     * Get or generate icon for an app
     */
    public Drawable getDrawableIconForPackage(ComponentName componentName) {
        // system icons, nothing to do
        if (mIconPackPackageName.equalsIgnoreCase("default")) {
            return getDefaultAppDrawable(componentName);
        }

        String drawable = mPackagesDrawables.get(componentName.toString());
        if (drawable != null) { //there is a custom icon
            int id = mIconPackres.getIdentifier(drawable, "drawable", mIconPackPackageName);
            if (id > 0) {
                return mIconPackres.getDrawable(id);
            }
        }

        //search first in cache
        Drawable systemIcon = cacheGetDrawable(componentName.toString());
        if (systemIcon != null) {
            return systemIcon;
        }

        systemIcon = getDefaultAppDrawable(componentName);
//        if (systemIcon instanceof BitmapDrawable) {
//            Drawable generated = generateBitmap(systemIcon);
//            cacheStoreDrawable(componentName.toString(), generated);
//            return generated;
//        }
        return systemIcon;
    }

    public Bitmap getBitmapIcon(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        return null;
    }

    private Drawable generateBitmap(Drawable defaultBitmap) {
        // if no support images in the icon pack return the bitmap itself
        if (mBackImages.size() == 0) {
            return defaultBitmap;
        }

        // select a random background image
        Random r = new Random();
        int backImageInd = r.nextInt(mBackImages.size());
        Bitmap backImage = mBackImages.get(backImageInd);
        int w = backImage.getWidth();
        int h = backImage.getHeight();

        // create a bitmap for the result
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        // draw the background first
        canvas.drawBitmap(backImage, 0, 0, null);

        // scale original icon
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(((BitmapDrawable) defaultBitmap).getBitmap(), (int) (w * mFactor), (int) (h * mFactor), false);

        if (mMaskImage != null) {
            // draw the scaled bitmap with mask
            Bitmap mutableMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas maskCanvas = new Canvas(mutableMask);
            maskCanvas.drawBitmap(mMaskImage, 0, 0, new Paint());

            // paint the bitmap with mask into the result
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            canvas.drawBitmap(scaledBitmap, (w - scaledBitmap.getWidth()) / 2, (h - scaledBitmap.getHeight()) / 2, null);
            canvas.drawBitmap(mutableMask, 0, 0, paint);
            paint.setXfermode(null);
        } else { // draw the scaled bitmap without mask
            canvas.drawBitmap(scaledBitmap, (w - scaledBitmap.getWidth()) / 2, (h - scaledBitmap.getHeight()) / 2, null);
        }

        // paint the front
        if (mFrontImage != null) {
            canvas.drawBitmap(mFrontImage, 0, 0, null);
        }

        return new BitmapDrawable(mIconPackres, result);
    }

    /**
     * Scan for installed icons packs
     */
    private void loadAvailableiconPacks() {
        List<ResolveInfo> launcherThemes = mPm.queryIntentActivities(new Intent("fr.neamar.kiss.THEMES"), PackageManager.GET_META_DATA);
        List<ResolveInfo> adwlauncherThemes = mPm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"), PackageManager.GET_META_DATA);

        launcherThemes.addAll(adwlauncherThemes);

        for (ResolveInfo ri : launcherThemes) {
            String packageName = ri.activityInfo.packageName;
            try {
                ApplicationInfo ai = mPm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                String name = mPm.getApplicationLabel(ai).toString();
                mIconPacks.put(packageName, name);
            } catch (PackageManager.NameNotFoundException e) {
                // shouldn't happen
                Log.e(TAG, "Unable to found package " + packageName + e);
            }
        }
    }

    public HashMap<String, String> getIconPacks() {
        return mIconPacks;
    }

    private boolean isDrawableInCache(String key) {
        File drawableFile = cacheGetFileName(key);
        return drawableFile.isFile();
    }

    private boolean cacheStoreDrawable(String key, Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            File drawableFile = cacheGetFileName(key);
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(drawableFile);
                ((BitmapDrawable) drawable).getBitmap().compress(CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Unable to store drawable in cache " + e);
            }
        }
        return false;
    }

    private Drawable cacheGetDrawable(String key) {
        if (!isDrawableInCache(key)) {
            return null;
        }

        FileInputStream fis;
        try {
            fis = new FileInputStream(cacheGetFileName(key));
            BitmapDrawable drawable =
                    new BitmapDrawable(mContext.getResources(), BitmapFactory.decodeStream(fis));
            fis.close();
            return drawable;
        } catch (Exception e) {
            Log.e(TAG, "Unable to get drawable from cache " + e);
        }

        return null;
    }

    /**
     * create path for icons cache like this
     * {cacheDir}/icons/{icons_pack_package_name}_{key_hash}.png
     */
    private File cacheGetFileName(String key) {
        return new File(getIconsCacheDir() + mIconPackPackageName + "_" + key.hashCode() + ".png");
    }

    private File getIconsCacheDir() {
        return new File(mContext.getCacheDir().getPath() + "/icons/");
    }

    /**
     * Clear cache
     */
    private void clearCache() {
        File cacheDir = getIconsCacheDir();
        if (!cacheDir.isDirectory()) {
            return;
        }

        for (File item : cacheDir.listFiles()) {
            if (!item.delete()) {
                Log.w(TAG, "Failed to delete file: " + item.getAbsolutePath());
            }
        }
    }
}
