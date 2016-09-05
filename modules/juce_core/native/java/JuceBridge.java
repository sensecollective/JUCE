package com.juce;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
$$JuceAndroidMidiImports$$ // If you get an error here, you need to re-save your project with the Projucer!

public class JuceBridge
{
    static
    {
        System.loadLibrary("juce_jni");
    }

    private Context activityContext;
    private static JuceBridge instance;
    private Map<String, ComponentPeerView> componentPeerViewMap;
    private Map<String, JuceViewHolder> juceViewHolderMap;
    private JuceViewHolder juceViewHolder;

    private static class Holder {
        static final JuceBridge INSTANCE = new JuceBridge();
    }

    private JuceBridge()
    {
        juceViewHolderMap = new HashMap<String, JuceViewHolder>();
        permissionCallbackPtrMap = new HashMap<Integer, Long>();
    }

    public static JuceBridge getInstance()
    {
        return Holder.INSTANCE;
    }

    public synchronized void setActivityContext(Context c)
    {
        activityContext = c;
    }

    public boolean appInitialised()
    {
        return hasInitialised();
    }
    private native boolean hasInitialised();

    //==============================================================================

    public void hideActionBar()
    {
        // get "getActionBar" method
        java.lang.reflect.Method getActionBarMethod = null;
        try
        {
            getActionBarMethod = activityContext.getClass().getMethod ("getActionBar");
        }
        catch (SecurityException e)     { return; }
        catch (NoSuchMethodException e) { return; }
        if (getActionBarMethod == null) return;

        // invoke "getActionBar" method
        Object actionBar = null;
        try
        {
            actionBar = getActionBarMethod.invoke (activityContext);
        }
        catch (java.lang.IllegalArgumentException e) { return; }
        catch (java.lang.IllegalAccessException e) { return; }
        catch (java.lang.reflect.InvocationTargetException e) { return; }
        if (actionBar == null) return;

        // get "hide" method
        java.lang.reflect.Method actionBarHideMethod = null;
        try
        {
            actionBarHideMethod = actionBar.getClass().getMethod ("hide");
        }
        catch (SecurityException e)     { return; }
        catch (NoSuchMethodException e) { return; }
        if (actionBarHideMethod == null) return;

        // invoke "hide" method
        try
        {
            actionBarHideMethod.invoke (actionBar);
        }
        catch (java.lang.IllegalArgumentException e) {}
        catch (java.lang.IllegalAccessException e) {}
        catch (java.lang.reflect.InvocationTargetException e) {}
    }

    private java.util.Timer keepAliveTimer;
    private boolean isScreenSaverEnabled;

    public final void setScreenSaver (boolean enabled) {
        if (isScreenSaverEnabled != enabled)
        {
            isScreenSaverEnabled = enabled;

            if (keepAliveTimer != null) {
                keepAliveTimer.cancel();
                keepAliveTimer = null;
            }

            if (enabled) {
                ((Activity) activityContext).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                ((Activity) activityContext).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                // If no user input is received after about 3 seconds, the OS will lower the
                // task's priority, so this timer forces it to be kept active.
                keepAliveTimer = new java.util.Timer();

                keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        android.app.Instrumentation instrumentation = new android.app.Instrumentation();

                        try {
                            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_UNKNOWN);
                        } catch (Exception e) {
                        }
                    }
                }, 2000, 2000);
            }
        }
    }
    //==============================================================================
    public final void showMessageBox (String title, String message, final long callback)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder (activityContext);
        builder.setTitle (title)
                .setMessage (message)
                .setCancelable (true)
                .setPositiveButton ("OK", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                        alertDismissed (callback, 0);
                    }
                });

        builder.create().show();
    }

    public final void showOkCancelBox (String title, String message, final long callback)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder (activityContext);
        builder.setTitle (title)
                .setMessage (message)
                .setCancelable (true)
                .setPositiveButton ("OK", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                        alertDismissed (callback, 1);
                    }
                })
                .setNegativeButton ("Cancel", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                        alertDismissed (callback, 0);
                    }
                });

        builder.create().show();
    }

    public final void showYesNoCancelBox (String title, String message, final long callback)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder (activityContext);
        builder.setTitle (title)
                .setMessage (message)
                .setCancelable (true)
                .setPositiveButton ("Yes", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                        alertDismissed (callback, 1);
                    }
                })
                .setNegativeButton ("No", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                        alertDismissed (callback, 2);
                    }
                })
                .setNeutralButton ("Cancel", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                        alertDismissed (callback, 0);
                    }
                });

        builder.create().show();
    }

    //==============================================================================

    public boolean isPermissionDeclaredInManifest (int permissionID)
    {
        String permissionToCheck = getAndroidPermissionName(permissionID);

        try
        {
            PackageInfo info = activityContext.getPackageManager().getPackageInfo(activityContext.getPackageName(), PackageManager.GET_PERMISSIONS);

            if (info.requestedPermissions != null)
                for (String permission : info.requestedPermissions)
                    if (permission.equals (permissionToCheck))
                        return true;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.d ("JUCE", "isPermissionDeclaredInManifest: PackageManager.NameNotFoundException = " + e.toString());
        }

        Log.d ("JUCE", "isPermissionDeclaredInManifest: could not find requested permission " + permissionToCheck);
        return false;
    }

    public boolean isPermissionGranted(int permissionID) {
        return ContextCompat.checkSelfPermission(activityContext, getAndroidPermissionName(permissionID)) == PackageManager.PERMISSION_GRANTED;
    }

    // these have to match the values of enum PermissionID in C++ class RuntimePermissions:
    private static final int JUCE_PERMISSIONS_RECORD_AUDIO = 1;
    private static final int JUCE_PERMISSIONS_BLUETOOTH_MIDI = 2;

    private static String getAndroidPermissionName (int permissionID)
    {
        switch (permissionID)
        {
            case JUCE_PERMISSIONS_RECORD_AUDIO:     return Manifest.permission.RECORD_AUDIO;
            case JUCE_PERMISSIONS_BLUETOOTH_MIDI:   return Manifest.permission.ACCESS_COARSE_LOCATION;
        }

        // unknown permission ID!
        assert false;
        return new String();
    }

    private Map<Integer, Long> permissionCallbackPtrMap;

    public void requestRuntimePermission (int permissionID, long ptrToCallback)
    {
        String permissionName = getAndroidPermissionName (permissionID);

        if (ContextCompat.checkSelfPermission (activityContext, permissionName) != PackageManager.PERMISSION_GRANTED)
        {
            // remember callbackPtr, request permissions, and let onRequestPermissionResult call callback asynchronously
            permissionCallbackPtrMap.put (permissionID, ptrToCallback);
            ActivityCompat.requestPermissions ((Activity) activityContext, new String[]{permissionName}, permissionID);
        }
        else
        {
            // permissions were already granted before, we can call callback directly
            androidRuntimePermissionsCallback (true, ptrToCallback);
        }
    }

    private native void androidRuntimePermissionsCallback (boolean permissionWasGranted, long ptrToCallback);

    public void onRequestPermissionsResult (int permissionID, String permissions[], int[] grantResults)
    {
        boolean permissionsGranted = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);

        if (! permissionsGranted)
            Log.d ("JUCE", "onRequestPermissionsResult: runtime permission was DENIED: " + getAndroidPermissionName (permissionID));

        Long ptrToCallback = permissionCallbackPtrMap.get (permissionID);
        permissionCallbackPtrMap.remove (permissionID);
        androidRuntimePermissionsCallback (permissionsGranted, ptrToCallback);
    }

    //==============================================================================
    public static class MidiPortID extends Object
    {
        public MidiPortID (int index, boolean direction)
        {
            androidIndex = index;
            isInput = direction;
        }

        public int androidIndex;
        public boolean isInput;

        @Override
        public int hashCode()
        {
            Integer i = new Integer (androidIndex);
            return i.hashCode() * (isInput ? -1 : 1);
        }

        @Override
        public boolean equals (Object obj)
        {
            if (obj == null)
                return false;

            if (getClass() != obj.getClass())
                return false;

            MidiPortID other = (MidiPortID) obj;
            return (androidIndex == other.androidIndex && isInput == other.isInput);
        }
    }

    public interface JuceMidiPort
    {
        boolean isInputPort();

        // start, stop does nothing on an output port
        void start();
        void stop();

        void close();
        MidiPortID getPortId();

        // send will do nothing on an input port
        void sendMidi (byte[] msg, int offset, int count);
    }

    //==============================================================================
    $$JuceAndroidMidiCode$$ // If you get an error here, you need to re-save your project with the Projucer!

    //==============================================================================
    public final class ComponentPeerView extends ViewGroup
            implements View.OnFocusChangeListener
    {
        public ComponentPeerView (Context context, boolean opaque_, long host)
        {
            super (context);
            this.host = host;
            setWillNotDraw (false);
            opaque = opaque_;

            setFocusable (true);
            setFocusableInTouchMode (true);
            setOnFocusChangeListener (this);
            requestFocus();

            // swap red and blue colours to match internal opengl texture format
            ColorMatrix colorMatrix = new ColorMatrix();

            float[] colorTransform = { 0,    0,    1.0f, 0,    0,
                    0,    1.0f, 0,    0,    0,
                    1.0f, 0,    0,    0,    0,
                    0,    0,    0,    1.0f, 0 };

            colorMatrix.set (colorTransform);
            paint.setColorFilter (new ColorMatrixColorFilter(colorMatrix));
        }

        //==============================================================================
        private native void handlePaint (long host, Canvas canvas, Paint paint);

        @Override
        public void onDraw (Canvas canvas)
        {
            handlePaint (host, canvas, paint);
        }

        @Override
        public boolean isOpaque()
        {
            return opaque;
        }

        private boolean opaque;
        private long host;
        private Paint paint = new Paint();

        //==============================================================================
        private native void handleMouseDown (long host, int index, float x, float y, long time);
        private native void handleMouseDrag (long host, int index, float x, float y, long time);
        private native void handleMouseUp   (long host, int index, float x, float y, long time);

        @Override
        public boolean onTouchEvent (MotionEvent event)
        {
            int action = event.getAction();
            long time = event.getEventTime();

            switch (action & MotionEvent.ACTION_MASK)
            {
                case MotionEvent.ACTION_DOWN:
                    handleMouseDown (host, event.getPointerId(0), event.getX(), event.getY(), time);
                    return true;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    handleMouseUp (host, event.getPointerId(0), event.getX(), event.getY(), time);
                    return true;

                case MotionEvent.ACTION_MOVE:
                {
                    int n = event.getPointerCount();
                    for (int i = 0; i < n; ++i)
                        handleMouseDrag (host, event.getPointerId(i), event.getX(i), event.getY(i), time);

                    return true;
                }

                case MotionEvent.ACTION_POINTER_UP:
                {
                    int i = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    handleMouseUp (host, event.getPointerId(i), event.getX(i), event.getY(i), time);
                    return true;
                }

                case MotionEvent.ACTION_POINTER_DOWN:
                {
                    int i = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    handleMouseDown (host, event.getPointerId(i), event.getX(i), event.getY(i), time);
                    return true;
                }

                default:
                    break;
            }

            return false;
        }

        //==============================================================================
        private native void handleKeyDown (long host, int keycode, int textchar);
        private native void handleKeyUp (long host, int keycode, int textchar);

        public void showKeyboard (String type)
        {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService (Context.INPUT_METHOD_SERVICE);

            if (imm != null)
            {
                if (type.length() > 0)
                {
                    imm.showSoftInput (this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    imm.setInputMethod (getWindowToken(), type);
                }
                else
                {
                    imm.hideSoftInputFromWindow (getWindowToken(), 0);
                }
            }
        }

        @Override
        public boolean onKeyDown (int keyCode, KeyEvent event)
        {
            switch (keyCode)
            {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    return super.onKeyDown (keyCode, event);

                default: break;
            }

            handleKeyDown (host, keyCode, event.getUnicodeChar());
            return true;
        }

        @Override
        public boolean onKeyUp (int keyCode, KeyEvent event)
        {
            handleKeyUp (host, keyCode, event.getUnicodeChar());
            return true;
        }

        @Override
        public boolean onKeyMultiple (int keyCode, int count, KeyEvent event)
        {
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN || event.getAction() != KeyEvent.ACTION_MULTIPLE)
                return super.onKeyMultiple (keyCode, count, event);

            if (event.getCharacters() != null)
            {
                int utf8Char = event.getCharacters().codePointAt (0);
                handleKeyDown (host, utf8Char, utf8Char);
                return true;
            }

            return false;
        }

        // this is here to make keyboard entry work on a Galaxy Tab2 10.1
        @Override
        public InputConnection onCreateInputConnection (EditorInfo outAttrs)
        {
            outAttrs.actionLabel = "";
            outAttrs.hintText = "";
            outAttrs.initialCapsMode = 0;
            outAttrs.initialSelEnd = outAttrs.initialSelStart = -1;
            outAttrs.label = "";
            outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            outAttrs.inputType = InputType.TYPE_NULL;

            return new BaseInputConnection(this, false);
        }

        //==============================================================================
        @Override
        protected void onSizeChanged (int w, int h, int oldw, int oldh)
        {
            super.onSizeChanged (w, h, oldw, oldh);
            viewSizeChanged (host);
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom)
        {
            for (int i = getChildCount(); --i >= 0;)
                requestTransparentRegion (getChildAt (i));
        }

        private native void viewSizeChanged (long host);

        @Override
        public void onFocusChange (View v, boolean hasFocus)
        {
            if (v == this)
                focusChanged (host, hasFocus);
        }

        private native void focusChanged (long host, boolean hasFocus);

        public void setViewName (String newName)    {}

        public boolean isVisible()                  { return getVisibility() == VISIBLE; }
        public void setVisible (boolean b)          { setVisibility (b ? VISIBLE : INVISIBLE); }

        public boolean containsPoint (int x, int y)
        {
            return true; //xxx needs to check overlapping views
        }
    }
    //==============================================================================
    public native void deliverMessage (long value);
    private android.os.Handler messageHandler = new android.os.Handler();

    public final void postMessage (long value)
    {
        messageHandler.post (new MessageCallback (value));
    }

    private final class MessageCallback  implements Runnable
    {
        public MessageCallback (long value_)        { value = value_; }
        public final void run()                     { deliverMessage (value); }

        private long value;
    }

    public MidiDeviceManager getMidiDeviceManager() {
        return midiDeviceManager;
    }

    public void setMidiDeviceManager(MidiDeviceManager midiDeviceManager) {
        this.midiDeviceManager = midiDeviceManager;
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    public void setBluetoothManager(BluetoothManager bluetoothManager) {
        this.bluetoothManager = bluetoothManager;
    }


    //==============================================================================
    private MidiDeviceManager midiDeviceManager = null;
    private BluetoothManager bluetoothManager = null;

    public JuceViewHolder createViewForComponent (String componentName, Context c)
    {
        JuceViewHolder juceViewHolder;
        // Check if view exists in map before creating new
        if ((juceViewHolder = juceViewHolderMap.get (componentName)) == null)
        {
           juceViewHolder = new JuceViewHolder(c);
            Log.d("JuceBridge", "Type of context is "+c.getClass().getName());
            juceViewHolderMap.put(componentName, juceViewHolder);
        }
        return juceViewHolder;
    }

    public JuceViewHolder createViewForDefaultComponent (Context c)
    {
        return createViewForComponent(null, c);
    }

    public JuceViewHolder newViewHolder (Context c)
    {
        return juceViewHolder = new JuceViewHolder(c);
    }

    public final ComponentPeerView createNewView (boolean opaque, long host, String componentName)
    {
        ComponentPeerView v = null;
        Log.d("JuceBridge", "Add view to JuceViewHolder with key="+componentName);
        JuceViewHolder juceView = juceViewHolderMap.get(componentName);
        if (juceView != null)
        {
            v = new ComponentPeerView (juceView.getContext(), opaque, host);
            juceView.addView(v);
        }
        else
        {
            Log.d("JuceBridge", "No JuceViewHolder found for key=" + componentName);
            Log.d("JuceBridge", "Trying null key");
            juceView = juceViewHolderMap.get(null);
            if (juceView != null)
            {
                v = new ComponentPeerView (juceView.getContext(), opaque, host);
                juceView.addView(v);
                Log.d("JuceBridge", "Added view to JuceViewHolder with null key");
            }
            else
            {
                Log.d("JuceBridge", "No JuceViewHolder found for null key");
            }
        }
        return v;
    }
    public final void deleteView (ComponentPeerView view)
    {
        ViewGroup group = (ViewGroup) (view.getParent());

        if (group != null)
            group.removeView (view);
    }

    public final void deleteNativeSurfaceView (NativeSurfaceView view)
    {
        ViewGroup group = (ViewGroup) (view.getParent());

        if (group != null)
            group.removeView (view);
    }

    public void callAppLauncher()
    {
            if (!hasInitialised())
                launchApp(activityContext.getApplicationInfo().publicSourceDir,
                        activityContext.getApplicationInfo().dataDir);
    }

    public void setRequestedOrientation (int requestedOrientation)
    {
        ((Activity) activityContext).setRequestedOrientation (requestedOrientation);
    }

    public void finish()
    {
        ((Activity) activityContext).finish();
    }

    //==============================================================================
    private native void launchApp (String appFile, String appDataDir);
    public native void quitApp();
    public native void suspendApp();
    public native void resumeApp();
    public native void setScreenSize (int screenWidth, int screenHeight, int dpi);

    //==============================================================================
    public final void excludeClipRegion (android.graphics.Canvas canvas, float left, float top, float right, float bottom)
    {
        canvas.clipRect (left, top, right, bottom, android.graphics.Region.Op.DIFFERENCE);
    }

    //==============================================================================
    public static class NativeSurfaceView    extends SurfaceView
            implements SurfaceHolder.Callback
    {
        private long nativeContext = 0;

        NativeSurfaceView (Context context, long nativeContextPtr)
        {
            super (context);
            nativeContext = nativeContextPtr;
        }

        public Surface getNativeSurface()
        {
            Surface retval = null;

            SurfaceHolder holder = getHolder();
            if (holder != null)
                retval = holder.getSurface();

            return retval;
        }

        //==============================================================================
        @Override
        public void surfaceChanged (SurfaceHolder holder, int format, int width, int height)
        {
            surfaceChangedNative (nativeContext, holder, format, width, height);
        }

        @Override
        public void surfaceCreated (SurfaceHolder holder)
        {
            surfaceCreatedNative (nativeContext, holder);
        }

        @Override
        public void surfaceDestroyed (SurfaceHolder holder)
        {
            surfaceDestroyedNative (nativeContext, holder);
        }

        @Override
        protected void dispatchDraw (Canvas canvas)
        {
            super.dispatchDraw (canvas);
            dispatchDrawNative (nativeContext, canvas);
        }

        //==============================================================================
        @Override
        protected void onAttachedToWindow ()
        {
            super.onAttachedToWindow();
            getHolder().addCallback (this);
        }

        @Override
        protected void onDetachedFromWindow ()
        {
            super.onDetachedFromWindow();
            getHolder().removeCallback (this);
        }

        //==============================================================================
        private native void dispatchDrawNative (long nativeContextPtr, Canvas canvas);
        private native void surfaceCreatedNative (long nativeContextptr, SurfaceHolder holder);
        private native void surfaceDestroyedNative (long nativeContextptr, SurfaceHolder holder);
        private native void surfaceChangedNative (long nativeContextptr, SurfaceHolder holder,
                                                  int format, int width, int height);
    }

    public NativeSurfaceView createNativeSurfaceView (long nativeSurfacePtr)
    {
        return new NativeSurfaceView (this.activityContext, nativeSurfacePtr);
    }

    //==============================================================================

    public final boolean getScreenSaver()
    {
        return isScreenSaverEnabled;
    }

    //==============================================================================
    public final String getClipboardContent()
    {
        ClipboardManager clipboard = (ClipboardManager) activityContext.getSystemService (Context.CLIPBOARD_SERVICE);
        return clipboard.getText().toString();
    }

    public final void setClipboardContent (String newText)
    {
        ClipboardManager clipboard = (ClipboardManager) activityContext.getSystemService (Context.CLIPBOARD_SERVICE);
        clipboard.setText (newText);
    }

    public native void alertDismissed (long callback, int id);

    //==============================================================================
    public final int[] renderGlyph (char glyph, Paint paint, android.graphics.Matrix matrix, Rect bounds)
    {
        Path p = new Path();
        paint.getTextPath (String.valueOf (glyph), 0, 1, 0.0f, 0.0f, p);

        RectF boundsF = new RectF();
        p.computeBounds (boundsF, true);
        matrix.mapRect (boundsF);

        boundsF.roundOut (bounds);
        bounds.left--;
        bounds.right++;

        final int w = bounds.width();
        final int h = Math.max (1, bounds.height());

        Bitmap bm = Bitmap.createBitmap (w, h, Bitmap.Config.ARGB_8888);

        Canvas c = new Canvas (bm);
        matrix.postTranslate (-bounds.left, -bounds.top);
        c.setMatrix (matrix);
        c.drawPath (p, paint);

        final int sizeNeeded = w * h;
        if (cachedRenderArray.length < sizeNeeded)
            cachedRenderArray = new int [sizeNeeded];

        bm.getPixels (cachedRenderArray, 0, w, 0, 0, w, h);
        bm.recycle();
        return cachedRenderArray;
    }

    private int[] cachedRenderArray = new int [256];

    //==============================================================================
    public static class HTTPStream
    {
        public HTTPStream (HttpURLConnection connection_,
                           int[] statusCode, StringBuffer responseHeaders) throws IOException
        {
            connection = connection_;

            try
            {
                inputStream = new BufferedInputStream(connection.getInputStream());
            }
            catch (IOException e)
            {
                if (connection.getResponseCode() < 400)
                    throw e;
            }
            finally
            {
                statusCode[0] = connection.getResponseCode();
            }

            if (statusCode[0] >= 400)
                inputStream = connection.getErrorStream();
            else
                inputStream = connection.getInputStream();

            for (java.util.Map.Entry<String, java.util.List<String>> entry : connection.getHeaderFields().entrySet())
                if (entry.getKey() != null && entry.getValue() != null)
                    responseHeaders.append (entry.getKey() + ": "
                            + android.text.TextUtils.join (",", entry.getValue()) + "\n");
        }

        public final void release()
        {
            try
            {
                inputStream.close();
            }
            catch (IOException e)
            {}

            connection.disconnect();
        }

        public final int read (byte[] buffer, int numBytes)
        {
            int num = 0;

            try
            {
                num = inputStream.read (buffer, 0, numBytes);
            }
            catch (IOException e)
            {}

            if (num > 0)
                position += num;

            return num;
        }

        public final long getPosition()                 { return position; }
        public final long getTotalLength()              { return -1; }
        public final boolean isExhausted()              { return false; }
        public final boolean setPosition (long newPos)  { return false; }

        private HttpURLConnection connection;
        private InputStream inputStream;
        private long position;
    }

    public static final HTTPStream createHTTPStream (String address, boolean isPost, byte[] postData,
                                                     String headers, int timeOutMs, int[] statusCode,
                                                     StringBuffer responseHeaders, int numRedirectsToFollow,
                                                     String httpRequestCmd)
    {
        // timeout parameter of zero for HttpUrlConnection is a blocking connect (negative value for juce::URL)
        if (timeOutMs < 0)
            timeOutMs = 0;
        else if (timeOutMs == 0)
            timeOutMs = 30000;

        // headers - if not empty, this string is appended onto the headers that are used for the request. It must therefore be a valid set of HTML header directives, separated by newlines.
        // So convert headers string to an array, with an element for each line
        String headerLines[] = headers.split("\\n");

        for (;;)
        {
            try
            {
                HttpURLConnection connection = (HttpURLConnection) (new URL(address).openConnection());

                if (connection != null)
                {
                    try
                    {
                        connection.setInstanceFollowRedirects (false);
                        connection.setConnectTimeout (timeOutMs);
                        connection.setReadTimeout (timeOutMs);

                        // Set request headers
                        for (int i = 0; i < headerLines.length; ++i)
                        {
                            int pos = headerLines[i].indexOf (":");

                            if (pos > 0 && pos < headerLines[i].length())
                            {
                                String field = headerLines[i].substring (0, pos);
                                String value = headerLines[i].substring (pos + 1);

                                if (value.length() > 0)
                                    connection.setRequestProperty (field, value);
                            }
                        }

                        connection.setRequestMethod (httpRequestCmd);
                        if (isPost)
                        {
                            connection.setDoOutput (true);

                            if (postData != null)
                            {
                                OutputStream out = connection.getOutputStream();
                                out.write(postData);
                                out.flush();
                            }
                        }

                        HTTPStream httpStream = new HTTPStream (connection, statusCode, responseHeaders);

                        // Process redirect & continue as necessary
                        int status = statusCode[0];

                        if (--numRedirectsToFollow >= 0
                                && (status == 301 || status == 302 || status == 303 || status == 307))
                        {
                            // Assumes only one occurrence of "Location"
                            int pos1 = responseHeaders.indexOf ("Location:") + 10;
                            int pos2 = responseHeaders.indexOf ("\n", pos1);

                            if (pos2 > pos1)
                            {
                                String newLocation = responseHeaders.substring(pos1, pos2);
                                // Handle newLocation whether it's absolute or relative
                                URL baseUrl = new URL (address);
                                URL newUrl = new URL (baseUrl, newLocation);
                                String transformedNewLocation = newUrl.toString();

                                if (transformedNewLocation != address)
                                {
                                    address = transformedNewLocation;
                                    // Clear responseHeaders before next iteration
                                    responseHeaders.delete (0, responseHeaders.length());
                                    continue;
                                }
                            }
                        }

                        return httpStream;
                    }
                    catch (Throwable e)
                    {
                        connection.disconnect();
                    }
                }
            }
            catch (Throwable e) {}

            return null;
        }
    }

    public final void launchURL (String url)
    {
            ((Activity) activityContext).startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    public static final String getLocaleValue (boolean isRegion)
    {
        java.util.Locale locale = java.util.Locale.getDefault();

        return isRegion ? locale.getDisplayCountry  (java.util.Locale.US)
                : locale.getDisplayLanguage (java.util.Locale.US);
    }

    private static final String getFileLocation (String type)
    {
        return Environment.getExternalStoragePublicDirectory (type).getAbsolutePath();
    }

    public static final String getDocumentsFolder()  { return Environment.getDataDirectory().getAbsolutePath(); }
    public static final String getPicturesFolder()   { return getFileLocation (Environment.DIRECTORY_PICTURES); }
    public static final String getMusicFolder()      { return getFileLocation (Environment.DIRECTORY_MUSIC); }
    public static final String getMoviesFolder()     { return getFileLocation (Environment.DIRECTORY_MOVIES); }
    public static final String getDownloadsFolder()  { return getFileLocation (Environment.DIRECTORY_DOWNLOADS); }

    //==============================================================================
    private final class SingleMediaScanner  implements MediaScannerConnection.MediaScannerConnectionClient
    {
        public SingleMediaScanner (Context context, String filename)
        {
            file = filename;
            msc = new MediaScannerConnection (context, this);
            msc.connect();
        }

        @Override
        public void onMediaScannerConnected()
        {
            msc.scanFile (file, null);
        }

        @Override
        public void onScanCompleted (String path, Uri uri)
        {
            msc.disconnect();
        }

        private MediaScannerConnection msc;
        private String file;
    }

    public final void scanFile (String filename)
    {
        new SingleMediaScanner (activityContext, filename);
    }

    public final Typeface getTypeFaceFromAsset (String assetName)
    {
        try
        {
            return Typeface.createFromAsset (activityContext.getResources().getAssets(), assetName);
        }
        catch (Throwable e) {}

        return null;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex (byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];

        for (int j = 0; j < bytes.length; ++j)
        {
            int v = bytes[j] & 0xff;
            hexChars[j * 2]     = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0f];
        }

        return new String (hexChars);
    }

    final private java.util.Map dataCache = new java.util.HashMap();

    synchronized private final File getDataCacheFile (byte[] data)
    {
        try
        {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance ("MD5");
            digest.update (data);

            String key = bytesToHex (digest.digest());

            if (dataCache.containsKey (key))
                return (File) dataCache.get (key);

            File f = new File (activityContext.getCacheDir(), "bindata_" + key);
            f.delete();
            FileOutputStream os = new FileOutputStream (f);
            os.write (data, 0, data.length);
            dataCache.put (key, f);
            return f;
        }
        catch (Throwable e) {}

        return null;
    }

    public final void clearDataCache()
    {
        java.util.Iterator it = dataCache.values().iterator();

        while (it.hasNext())
        {
            File f = (File) it.next();
            f.delete();
        }
    }

    public final Typeface getTypeFaceFromByteArray (byte[] data)
    {
        try
        {
            File f = getDataCacheFile (data);

            if (f != null)
                return Typeface.createFromFile (f);
        }
        catch (Exception e)
        {
            Log.e ("JUCE", e.toString());
        }

        return null;
    }

    public final int getAndroidSDKVersion()
    {
        return android.os.Build.VERSION.SDK_INT;
    }

    public final String audioManagerGetProperty (String property)
    {
        Object obj = activityContext.getSystemService (Context.AUDIO_SERVICE);
        if (obj == null)
            return null;

        java.lang.reflect.Method method;

        try
        {
            method = obj.getClass().getMethod ("getProperty", String.class);
        }
        catch (SecurityException e)     { return null; }
        catch (NoSuchMethodException e) { return null; }

        if (method == null)
            return null;

        try
        {
            return (String) method.invoke (obj, property);
        }
        catch (java.lang.IllegalArgumentException e) {}
        catch (java.lang.IllegalAccessException e) {}
        catch (java.lang.reflect.InvocationTargetException e) {}

        return null;
    }

    public final int setCurrentThreadPriority (int priority)
    {
        android.os.Process.setThreadPriority (android.os.Process.myTid(), priority);
        return android.os.Process.getThreadPriority (android.os.Process.myTid());
    }

    public final boolean hasSystemFeature (String property)
    {
        return activityContext.getPackageManager().hasSystemFeature (property);
    }

    private static class JuceThread extends Thread
    {
        public JuceThread (long host, String threadName, long threadStackSize)
        {
            super (null, null, threadName, threadStackSize);
            _this = host;
        }

        public void run()
        {
            runThread(_this);
        }

        private native void runThread (long host);
        private long _this;
    }

    public final Thread createNewThread(long host, String threadName, long threadStackSize)
    {
        return new JuceThread(host, threadName, threadStackSize);
    }
}