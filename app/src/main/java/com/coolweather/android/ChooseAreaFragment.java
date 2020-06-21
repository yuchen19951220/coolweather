package com.coolweather.android;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;

import com.coolweather.android.db.City;
import com.coolweather.android.db.Country;
import com.coolweather.android.db.Province;
import com.coolweather.android.gson.Weather;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utility;

import org.jetbrains.annotations.NotNull;
import org.litepal.LitePal;
import org.litepal.crud.LitePalSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public  static final int LEVEL_PROVINCE=0;
    public static final int LEVEL_CITY=1;
    public static final int LEVEL_COUNTY=2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    //使用
    private ArrayAdapter<String> adapter;
    private List<String> dataList=new ArrayList<>();

    //省列表
    private List<Province> provinceList;
    //市列表
    private List<City> cityList;
    //县列表
    private List<Country> countryList;


    //选中的省份
    private Province selectedProvince;
    //选中的城市
    private City selectedCity;
    //当前选中的级别
    private int currentLevel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view=inflater.inflate(R.layout.choose_area,container,false);
        titleText=view.findViewById(R.id.title_text);
        backButton=view.findViewById(R.id.back_button);
        listView=view.findViewById(R.id.list_view);
        adapter=new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel==LEVEL_PROVINCE){
                    selectedProvince=provinceList.get(position);
                    queryCities();
                } else if(currentLevel==LEVEL_CITY){
                    selectedCity=cityList.get(position);
                    queryCounties();
                } else if (currentLevel==LEVEL_COUNTY){
                    String weatherId=countryList.get(position).getWeatherId();
                    if (getActivity() instanceof MainActivity){
                        //在主视图中
                        Intent intent =new Intent(getActivity(), WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    } else if (getActivity() instanceof WeatherActivity){
                        //在天气视图中打开 切换天气
                        WeatherActivity activity=(WeatherActivity) getActivity();
                        activity.drawerLayout.closeDrawer(GravityCompat.START);
                        activity.swipeRefresh.setRefreshing(true);
                        activity.requestWeather(weatherId);
                    }

                }
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentLevel==LEVEL_COUNTY){
                    queryCities();
                } else if(currentLevel==LEVEL_CITY){
                   queryPronvince();
                }
            }
        });

        //最开始展示的时province界面 并将level设为province
        queryPronvince();
    }

//查询全国所有的省 优先从数据库中查询 没有查询再去服务器上查询
    private void queryPronvince() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        // 从数据库中查询所有Province表中对象
        provinceList= LitePal.findAll(Province.class);
        if (provinceList.size()>0){
            dataList.clear();
            for (Province province :
                 provinceList) {
                dataList.add(province.getProvinceName());
            }
            //数据改变提示更新
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_PROVINCE;
        } else {
            String adress="http://guolin.tech/api/china";
            queryFromServer(adress,"province");
        }
    }

    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cityList=LitePal.where("provinceid=?",String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size()>0){
            dataList.clear();
            for (City city:
                cityList) {
                dataList.add(city.getCityName());
            }
            //数据更新 提示更新
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_CITY;
        } else {
            int proviceCode=selectedProvince.getProvinceCode();
            String address="http://guolin.tech/api/china/"+proviceCode;
            queryFromServer(address,"city");
        }
    }




    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countryList=LitePal.where("cityid=?",String.valueOf(selectedCity.getId())).find(Country.class);
        if (countryList.size()>0){
            dataList.clear();
            for (Country country :
               countryList ) {
                dataList.add(country.getCountryName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_COUNTY;
        } else {
            int provinceCode=selectedProvince.getProvinceCode();
            int cityCode=selectedCity.getCityCode();
            String address= "http://guolin.tech/api/china/"+provinceCode+"/"+cityCode;
            queryFromServer(address,"country");
        }
    }


    //根据传入的地址和类型从服务器上查询省市县数据 根据类型采用不同的函数解析结果
    private void queryFromServer(final String address, final String type) {
        showProgressDialog();
        HttpUtil.sendOkhttpRequest(address, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDiag();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseText=response.body().string();
                boolean result=false;
                if ("province".equals(type)){
                    //处于中国 查询到所有省份数据 并解析
                    result= Utility.handleProvinceResponse(responseText);
                } else if ("city".equals(type)){
                    //处于省份 查询所有市数据 并解析
                    result=Utility.handleCityResponse(responseText,selectedProvince.getId());
                } else if("country".equals(type)){
                    //处于市 查询所有县数据
                    result=Utility.handleCountryResponse(responseText,selectedCity.getId());
                }

                if(result){
                    //切换到主线程中更新
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDiag();
                            if ("province".equals(type)){
                                queryPronvince();
                            } else if("city".equals(type)){
                                queryCities();
                            } else if ("country".equals(type)){
                                queryCounties();
                            }
                        }
                    });
                }

            }
        });
    }
    //显示进度对话框
    private void showProgressDialog() {
        if(progressDialog==null){
            progressDialog= new ProgressDialog(getActivity());
            progressDialog.setMessage("加载");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    //关闭进程对话框
    private void closeProgressDiag() {
        if (progressDialog!=null){
            progressDialog.dismiss();
        }
    }


}



