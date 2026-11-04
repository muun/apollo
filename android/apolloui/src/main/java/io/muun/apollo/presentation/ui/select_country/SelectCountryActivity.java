package io.muun.apollo.presentation.ui.select_country;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SelectCountryActivityBinding;
import io.muun.apollo.domain.model.CountryInfo;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.MuunViewHolder;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SelectCountryActivity extends BaseActivity<SelectCountryPresenter>
        implements BaseView {

    private static final String COUNTRY_CODE_RESULT = "selected_country";

    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, SelectCountryActivity.class);
    }

    public static CountryInfo getCountryFromResult(Intent data) {
        return CountryInfo.findByCode(data.getStringExtra(COUNTRY_CODE_RESULT)).orElse(null);
    }

    private SelectCountryActivityBinding binding() {
        return (SelectCountryActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return SelectCountryActivityBinding::inflate;
    }

    private List<CountryInfo> countryInfoList;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        binding().header.attachToActivity(this);
        binding().header.showTitle(R.string.select_country);
        binding().header.setNavigation(Navigation.BACK);

        binding().list.setAdapter(new CountryAdapter());
        binding().list.setLayoutManager(new LinearLayoutManager(this));

        setUpCountryList();
    }

    private void setUpCountryList() {
        countryInfoList = CountryInfo.getAll();
        binding().list.getAdapter().notifyDataSetChanged();
    }

    private void onCountryItemClick(CountryInfo countryInfo) {
        final Intent intent = new Intent()
                .putExtra(COUNTRY_CODE_RESULT, countryInfo.countryCode);

        setResult(RESULT_OK, intent);
        finishActivity();
    }

    private class CountryAdapter extends RecyclerView.Adapter<MuunViewHolder<TextView>> {

        @NonNull
        @Override
        public MuunViewHolder<TextView> onCreateViewHolder(ViewGroup root, int viewType) {
            final TextView view = (TextView) LayoutInflater
                    .from(root.getContext())
                    .inflate(R.layout.country_info_item, root, false);

            return new MuunViewHolder<>(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MuunViewHolder<TextView> holder, int pos) {
            final TextView textView = holder.getView();
            final CountryInfo countryInfo = countryInfoList.get(pos);

            textView.setText(
                    String.format("%s (+%d)", countryInfo.countryName, countryInfo.countryNumber)
            );

            textView.setOnClickListener(v -> onCountryItemClick(countryInfo));
        }

        @Override
        public int getItemCount() {
            return (countryInfoList != null) ? countryInfoList.size() : 0;
        }
    }
}
