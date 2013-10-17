
package com.example.grassimageloader;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Thumbnails;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String TAG = "loader";
    private ListView mListView;
    private ArrayList<Long> mData = new ArrayList<Long>();
    private LruCache<Long, Bitmap> mCache;
    private ContentResolver mResolver;
    private MyAdapter mAdapter;
    private Bitmap mPlaceHolderBitmap;
    private static final int SAVE_BITMAP_TO_LRUCACHE = 0;
    private static final int SAVE_BITMAP_TO_WEAK_REFERENCE = 1;
    private int mSaveMode = -1;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            long id = (Long) msg.obj;
            mData.add(id);
            mAdapter.notifyDataSetChanged();

        };
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        mSaveMode = SAVE_BITMAP_TO_LRUCACHE;
        mSaveMode = SAVE_BITMAP_TO_WEAK_REFERENCE;
        mPlaceHolderBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.place_holder2);
        final int maxSize = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        mCache = new LruCache<Long, Bitmap>(maxSize) {
            @Override
            protected void entryRemoved(boolean evicted, Long key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);

            }

            @Override
            protected int sizeOf(Long key, Bitmap value) {
                Log.i(TAG, "sizeOf: key=" + key + " value=" + value.getByteCount() / 1024
                        + " putCount=" + putCount() + " evictionCount=" + evictionCount() + " size"
                        + this.size() + " maxSize=" + maxSize);
                return value.getByteCount() / 1024;
            }
        };

        mResolver = getContentResolver();
        mAdapter = new MyAdapter();
        mListView = (ListView) findViewById(R.id.listview);
        mListView.setAdapter(mAdapter);
        scanImages();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private Bitmap getBitmapFromCache(long id) {
        return mCache.get(id);
    }

    private void addBitmapToCache(long id, Bitmap map) {
        if (mCache.get(id) == null) {
            mCache.put(id, map);
        }
    }

    private void scanImages() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                String[] proj = {
                        Media._ID, Images.Media.TITLE,
                        Images.Media.DISPLAY_NAME, Images.Media.DESCRIPTION,
                        Images.Media.DATA, Images.Media.MINI_THUMB_MAGIC,
                        Images.Media.MIME_TYPE
                };
                Cursor cursor = Media.query(mResolver,
                        Images.Media.EXTERNAL_CONTENT_URI, proj);
                int count = cursor.getCount();
                int number = 0;
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor
                        .moveToNext()) {
                    number++;
                    // if(number == 100) return;
                    long origid = cursor.getLong(cursor
                            .getColumnIndex(Images.Media._ID));
                    String title = cursor.getString(cursor
                            .getColumnIndex(Images.Media.TITLE));
                    String displayName = cursor.getString(cursor
                            .getColumnIndex(Images.Media.DISPLAY_NAME));
                    String des = cursor.getString(cursor
                            .getColumnIndex(Images.Media.DESCRIPTION));
                    String path = cursor.getString(cursor
                            .getColumnIndex(Images.Media.DATA));
                    String thumbId = cursor.getString(cursor
                            .getColumnIndex(Images.Media.MINI_THUMB_MAGIC));
                    String miniType = cursor.getString(cursor
                            .getColumnIndex(Images.Media.MIME_TYPE));
                    Message mes = mHandler.obtainMessage();
                    mes.obj = origid;
                    mHandler.sendMessage(mes);
                }
                cursor.close();
            }
        }).start();
    }

    class MyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView item = null;
            if (convertView == null) {
                item = new ImageView(MainActivity.this);
                Log.i("grass", "---------------------getView position="+position+" item="+item.hashCode());
                item.setScaleType(ScaleType.CENTER_INSIDE);
                AbsListView.LayoutParams params = new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT);
                item.setLayoutParams(params);
            } else {
                Log.i("grass", "getView position="+position+" item="+convertView.hashCode());
                item = (ImageView) convertView;
            }
            Log.i("item", "getView=" + item + " pos=" + position);
            long id = mData.get(position);
            switch (mSaveMode) {
                case SAVE_BITMAP_TO_LRUCACHE:
                    Bitmap map = getBitmapFromCache(id);
                    if (null != map) {
                        item.setImageBitmap(map);
                    } else {
                        item.setImageResource(R.drawable.place_holder2);
                        new BitmapWorkerTask2().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                                id);
                    }
                    break;
                case SAVE_BITMAP_TO_WEAK_REFERENCE:
                     loadBitmap(id, item);
                    break;

                default:
                    break;
            }
            return item;
        }

        public void loadBitmap(long resId, ImageView imageView) {
            Log.i("grass", "loadBitmap resid="+resId+"   imageView="+imageView.hashCode());
            if (cancelPotentialWork(resId, imageView)) {
                
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);

                final AsyncDrawable asyncDrawable =

                        new AsyncDrawable(getResources(), mPlaceHolderBitmap, task);

                imageView.setImageDrawable(asyncDrawable);

                task.execute(resId);

            }

        }

    }

    static class AsyncDrawable extends BitmapDrawable {

        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,

                BitmapWorkerTask bitmapWorkerTask) {

            super(res, bitmap);

            bitmapWorkerTaskReference =

                    new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);

        }

        public BitmapWorkerTask getBitmapWorkerTask() {

            return bitmapWorkerTaskReference.get();

        }

    }

    public static boolean cancelPotentialWork(long data, ImageView imageView) {

        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        Log.i("grass", "cancelPotentialWork task="+bitmapWorkerTask);
        if (bitmapWorkerTask != null) {

            final long bitmapData = bitmapWorkerTask.data;

            if (bitmapData != data) {

                // Cancel previous task

                bitmapWorkerTask.cancel(true);

            } else {

                // The same work is already in progress

                return false;

            }

        }

        // No task associated with the ImageView, or an existing task was
        // cancelled

        return true;

    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {

        if (imageView != null) {

            final Drawable drawable = imageView.getDrawable();

            if (drawable instanceof AsyncDrawable) {

                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;

                return asyncDrawable.getBitmapWorkerTask();

            }

        }

        return null;
    }

    class BitmapWorkerTask extends AsyncTask<Long, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private long data = -1;

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage
            // collected
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(Long... params) {
            data = params[0];
            Log.i("grass", "doInBackground");
            return Thumbnails.getThumbnail(mResolver, data, Thumbnails.MINI_KIND, null);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            Log.i("grass", "onPostExecute imageViewReference="+imageViewReference);
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                Log.i("grass", "onPostExecute imageViewReference="+imageViewReference+"  imageView="+imageView);
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }

    class BitmapWorkerTask2 extends AsyncTask<Long, Void, Void> {

        @Override
        protected Void doInBackground(Long... params) {
            long id = params[0];
            Bitmap map = Thumbnails.getThumbnail(mResolver, id, Thumbnails.MINI_KIND,
                    null);
            Log.i(TAG, "doInBackground id=" + id + "  map=" + map);
            if (null != map) {
                addBitmapToCache(id, map);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
            return null;
        }

    }

}
