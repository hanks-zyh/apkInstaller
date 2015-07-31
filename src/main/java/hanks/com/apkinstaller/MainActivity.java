package hanks.com.apkinstaller;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    private ProgressBar mInstallProgress;
    private RecyclerView mRecyclerView;
    private ApkInfoAdapter mAdapter;
    private Button mInstallButton;

    private ArrayList<ApkModel> mApkList = new ArrayList<>();
    private ArrayList<String> mInsatllPathList = new ArrayList<>();
    private List<ApkModel> installAppList  = new ArrayList<>();

    private static final int MSG_UPDATE_LIST = 30;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.what == MSG_UPDATE_LIST){
                mAdapter.notifyDataSetChanged();
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInstallButton = (Button) findViewById(R.id.btn_install);
        mRecyclerView = (RecyclerView) findViewById(R.id.listview);
        mInstallProgress = (ProgressBar) findViewById(R.id.progress_install);

        mAdapter = new ApkInfoAdapter();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);

        getData();


        mInstallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installApkforList();
            }
        });
    }

    private void installApkforList() {
        if (mInsatllPathList.size() <= 0) {
            showToast("至少选择一个apk");
            return;
        }


        //install
        mInstallProgress.setMax(mInsatllPathList.size());
        mInstallButton.setText("0/"+mInsatllPathList.size());
        for (String path : mInsatllPathList) {
            new InstallTask().execute(path);
        }
    }

    private void getData() {
        final Uri uri = MediaStore.Files.getContentUri("external");
        final String selection = MediaStore.Files.FileColumns.DATA + " LIKE '%.apk'";
        final String sortOrder = MediaStore.Files.FileColumns.TITLE + " asc";

        final String[] columns = new String[]{
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
        };
        final String path = "storage/sdcard1/Download";
        new AsyncTask<String, String, String>() {
            @Override
            protected String doInBackground(String... params) {
                installAppList = getAppList(getApplicationContext());
                Cursor cursor = getContentResolver().query(uri, columns, selection, null, sortOrder);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String data = cursor.getString(1);
                    long size = cursor.getLong(2);
                    long lastModify = cursor.getLong(3);
                    mApkList.add(makeApkModel(data, lastModify, size));
                    handler.sendEmptyMessage(MSG_UPDATE_LIST);
                }
                cursor.close();
                return "";
            }


            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                mAdapter.notifyDataSetChanged();
            }
        }.execute(path);

    }

    private ApkModel makeApkModel(String filePath, long lastModify, long length) {
        ApkModel apkModel = new ApkModel();

        PackageManager pm = getPackageManager();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES);
        if (packageInfo != null) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            //重新设置sourceDir
            applicationInfo.sourceDir = filePath;
            applicationInfo.publicSourceDir = filePath;

            apkModel.packageName = applicationInfo.packageName; //包名
            apkModel.name = pm.getApplicationLabel(applicationInfo).toString();
            apkModel.icon = applicationInfo.loadIcon(pm); //图标
            apkModel.lastModify = lastModify; //文件最后修改时间
            apkModel.size = length; //文件大小
            apkModel.path = filePath;
            apkModel.isInstalled = isInstalledApk(apkModel.packageName, apkModel.versionCode);
        }
        return apkModel;
    }

    private boolean isInstalledApk(String packageName, int versionCode) {
        for (ApkModel apkModel : installAppList) {
            if (apkModel.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isApkFile(File file) {
        return file.getName().endsWith(".apk");
    }

    private boolean silentInstallApk(String filePath) {
        try {
            Process processe = Runtime.getRuntime().exec("su");
            OutputStream outputStream = processe.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
            dataOutputStream.writeBytes("chmod 777 " + filePath + "\n");
            dataOutputStream.writeBytes("pm install -r " + filePath);
            dataOutputStream.flush();
            dataOutputStream.close();
            int value = processe.waitFor();
            if (value == 0) {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void log(String msg) {
        Log.i("ins", msg);
    }

    /**
     * 返回用户已安装应用列表
     */
    public List<ApkModel> getAppList(Context context) {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packageInfos = pm.getInstalledPackages(0);
        List<ApkModel> appInfos = new ArrayList<>();
        for (PackageInfo packageInfo : packageInfos) {
            ApplicationInfo app = packageInfo.applicationInfo;
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                // 非系统应用
                PackageStats stats = new PackageStats(packageInfo.packageName);
                ApkModel appInfo = new ApkModel();
                appInfo.packageName = packageInfo.packageName;
                appInfo.versionCode = packageInfo.versionCode;
                appInfo.versionName = packageInfo.versionName;
                appInfo.icon = app.loadIcon(pm);
                appInfo.name = app.loadLabel(pm).toString();
                appInfo.cacheSize = stats.cacheSize;
                appInfo.dataSize = stats.dataSize;
                appInfos.add(appInfo);
            }
        }
        return appInfos;
    }

    class InstallTask extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            boolean success = silentInstallApk(params[0]);
            if (success) {
                mInsatllPathList.remove(params[0]);
            }
            return success;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                updateProgress();
            } else {
                showToast("安装失败!");
            }
        }


    }

    private void updateProgress() {
        int max = mInstallProgress.getMax();
        mInstallProgress.setProgress(max - mInsatllPathList.size());
        mInstallButton.setText((max - mInsatllPathList.size()) +"/"+ max);
    }

    class ApkInfoAdapter extends RecyclerView.Adapter<ApkInfoViewHolder> {
        @Override
        public ApkInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = View.inflate(parent.getContext(), R.layout.item_list_apkinfo, null);
            return new ApkInfoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ApkInfoViewHolder holder, int position) {
            final ApkModel apkModel = mApkList.get(position);
            holder.setApkName(apkModel.name);
            holder.setApkCreateAt(apkModel.lastModify);
            holder.setApkSize(apkModel.size);
            holder.setApkInstalled(apkModel.isInstalled);
            holder.setIcon(apkModel.icon);
            holder.setChecked(mInsatllPathList.contains(apkModel.path));
            holder.mChecked.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mInsatllPathList.contains(apkModel.path)) {
                        mInsatllPathList.remove(apkModel.path);
                    } else {
                        mInsatllPathList.add(apkModel.path);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mApkList.size();
        }
    }

    class ApkInfoViewHolder extends RecyclerView.ViewHolder {

        private TextView mApkInstallStatus;
        private TextView mApkSize;
        private TextView mApkCreateAt;
        private TextView mApkName;
        private ImageView mIcon;
        private CheckBox mChecked;

        public ApkInfoViewHolder(View itemView) {
            super(itemView);
            mIcon = (ImageView) itemView.findViewById(R.id.iv_icon);
            mApkName = (TextView) itemView.findViewById(R.id.tv_name);
            mApkCreateAt = (TextView) itemView.findViewById(R.id.tv_create_at);
            mApkSize = (TextView) itemView.findViewById(R.id.tv_size);
            mChecked = (CheckBox) itemView.findViewById(R.id.cb_checked);
            mApkInstallStatus = (TextView) itemView.findViewById(R.id.tv_installed);
        }

        public void setChecked(boolean isChecked) {
            mChecked.setChecked(isChecked);
        }

        public void setIcon(Drawable icon) {
            mIcon.setImageDrawable(icon);
        }

        public void setApkName(String name) {
            mApkName.setText(name);
        }

        public void setApkCreateAt(long createTime) {
            Date date = new Date(createTime);
            SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//24小时制
            mApkCreateAt.setText(sdformat.format(date));
        }

        public void setApkSize(long size) {
            mApkSize.setText(Formatter.formatFileSize(getApplicationContext(), size));
        }

        public void setApkInstalled(boolean isInstalled) {
            mApkInstallStatus.setText(isInstalled ? "已安装" : "未安装");
            mApkInstallStatus.setTextColor(isInstalled ? Color.GREEN : Color.RED);
        }
    }

    class ApkModel {
        String name;
        String packageName;
        String versionName;
        String path;
        int versionCode;
        long cacheSize;
        long dataSize;
        long lastModify;
        long size;
        Drawable icon;
        boolean isInstalled;
    }
}
