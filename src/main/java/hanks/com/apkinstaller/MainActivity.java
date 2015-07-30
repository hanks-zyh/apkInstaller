package hanks.com.apkinstaller;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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


    private RecyclerView mRecyclerView;
    private ApkInfoAdapter mAdapter;
    private ArrayList<ApkModel>   mApkList = new ArrayList<>();
    private List<ApkModel> installAppList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecyclerView = (RecyclerView) findViewById(R.id.listview);
        getData();
        mAdapter = new ApkInfoAdapter();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);
    }

    private void getData() {
        Uri uri = MediaStore.Files.getContentUri("external");

        String path = "storage/sdcard1/Download";
        new AsyncTask<String, String, String>() {

            @Override
            protected String doInBackground(String... params) {
                installAppList = getAppList(getApplicationContext());

                File dir = new File(params[0]);
                try {
                    if (dir.exists() && dir.isDirectory()) {
                        for (File file : dir.listFiles()) {
                            if (isApkFile(file)) {
                                mApkList.add(makeApkModel(file));
                            }
                        }
                    } else {
                        showToast("文件夹不存在=.=");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return "";
            }


            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);

                mAdapter.notifyDataSetChanged();
            }
        }.execute(path);

    }

    private ApkModel makeApkModel(File file) {
        ApkModel apkModel = new ApkModel();

        PackageManager pm = getPackageManager();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
        if (packageInfo != null) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            //重新设置sourceDir
            applicationInfo.sourceDir = file.getAbsolutePath();
            applicationInfo.publicSourceDir = file.getAbsolutePath();

            apkModel.packageName = applicationInfo.packageName; //包名
            apkModel.name = pm.getApplicationLabel(applicationInfo).toString();
            apkModel.icon = applicationInfo.loadIcon(pm); //图标
            apkModel.lastModify = file.lastModified(); //文件最后修改时间
            apkModel.size = file.length(); //文件大小
            apkModel.path = file.getAbsolutePath();
            apkModel.isInstalled = isInstalledApk(apkModel.packageName, apkModel.versionCode);
        }
        return apkModel;
    }

    private boolean isInstalledApk(String packageName, int versionCode) {
        for (ApkModel apkModel : installAppList) {
            if (apkModel.packageName.equals(packageName) && apkModel.versionCode == versionCode) {
                return true;
            }
        }
        return false;
    }

    private boolean isApkFile(File file) {
        return file.getName().endsWith(".apk");
    }

    private void silentInstallApk(File file) {
        try {
            Process processe = Runtime.getRuntime().exec("su");
            OutputStream outputStream = processe.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
            dataOutputStream.writeBytes("chmod 777 " + file.getPath() + "\n");
            dataOutputStream.writeBytes("pm install -r " + file.getPath());
            dataOutputStream.flush();
            dataOutputStream.close();
            int value = processe.waitFor();
            showToast("安装结果:" + value);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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

    class ApkInfoAdapter extends RecyclerView.Adapter<ApkInfoViewHolder> {
        @Override
        public ApkInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = View.inflate(parent.getContext(), R.layout.item_list_apkinfo, null);
            return new ApkInfoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ApkInfoViewHolder holder, int position) {
            ApkModel apkModel = mApkList.get(position);
            holder.setApkName(apkModel.name);
            holder.setApkCreateAt(apkModel.lastModify);
            holder.setApkSize(apkModel.size);
            holder.setApkInstalled(apkModel.isInstalled);
            holder.setIcon(apkModel.icon);
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

        public ApkInfoViewHolder(View itemView) {
            super(itemView);
            mIcon = (ImageView) itemView.findViewById(R.id.iv_icon);
            mApkName = (TextView) itemView.findViewById(R.id.tv_name);
            mApkCreateAt = (TextView) itemView.findViewById(R.id.tv_create_at);
            mApkSize = (TextView) itemView.findViewById(R.id.tv_size);
            mApkInstallStatus = (TextView) itemView.findViewById(R.id.tv_installed);
        }

        public void setIcon(Drawable icon){
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
