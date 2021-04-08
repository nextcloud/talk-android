/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.controllers;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController;
import com.nextcloud.talk.components.filebrowser.controllers.BrowserForAvatarController;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.userprofile.Scope;
import com.nextcloud.talk.models.json.userprofile.UserProfileData;
import com.nextcloud.talk.models.json.userprofile.UserProfileFieldsOverall;
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall;
import com.nextcloud.talk.ui.dialog.ScopeDialog;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.jetbrains.annotations.NotNull;
import org.parceler.Parcels;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AutoInjector(NextcloudTalkApplication.class)
public class ProfileController extends BaseController {
    private static final String TAG = ProfileController.class.getSimpleName();

    @Inject
    NcApi ncApi;

    @Inject
    UserUtils userUtils;
    private UserEntity currentUser;
    private boolean edit = false;
    private RecyclerView recyclerView;
    private UserInfoAdapter adapter;
    private UserProfileData userInfo;
    private ArrayList<String> editableFields = new ArrayList<>();

    public ProfileController() {
        super();
    }

    @NotNull
    protected View inflateView(@NotNull LayoutInflater inflater, @NotNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_profile, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.menu_profile, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        menu.findItem(R.id.edit).setVisible(editableFields.size() > 0);

        if (edit) {
            menu.findItem(R.id.edit).setTitle(R.string.save);
        } else {
            menu.findItem(R.id.edit).setTitle(R.string.edit);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.edit:
                if (edit) {
                    save();
                }

                edit = !edit;

                if (edit) {
                    item.setTitle(R.string.save);

                    getActivity().findViewById(R.id.emptyList).setVisibility(View.GONE);
                    getActivity().findViewById(R.id.userinfo_list).setVisibility(View.VISIBLE);

                    if (currentUser.isAvatarEndpointAvailable()) {
                        // TODO later avatar can also be checked via user fields, for now it is in Talk capability
                        getActivity().findViewById(R.id.avatar_buttons).setVisibility(View.VISIBLE);
                    }

                    ncApi.getEditableUserProfileFields(
                            ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                            ApiUtils.getUrlForUserFields(currentUser.getBaseUrl()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<UserProfileFieldsOverall>() {
                                @Override
                                public void onSubscribe(@NotNull Disposable d) {
                                }

                                @Override
                                public void onNext(@NotNull UserProfileFieldsOverall userProfileFieldsOverall) {
                                    editableFields = userProfileFieldsOverall.getOcs().getData();
                                    adapter.notifyDataSetChanged();
                                }

                                @Override
                                public void onError(@NotNull Throwable e) {
                                    edit = false;
                                }

                                @Override
                                public void onComplete() {

                                }
                            });
                } else {
                    item.setTitle(R.string.edit);
                    getActivity().findViewById(R.id.avatar_buttons).setVisibility(View.INVISIBLE);

                    if (adapter.filteredDisplayList.size() == 0) {
                        getActivity().findViewById(R.id.emptyList).setVisibility(View.VISIBLE);
                        getActivity().findViewById(R.id.userinfo_list).setVisibility(View.GONE);
                    }
                }

                adapter.notifyDataSetChanged();

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);

        recyclerView = getActivity().findViewById(R.id.userinfo_list);
        adapter = new UserInfoAdapter(null, getActivity().getResources().getColor(R.color.colorPrimary), this);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemViewCacheSize(20);

        currentUser = userUtils.getCurrentUser();
        String credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());

        getActivity().findViewById(R.id.avatar_upload).setOnClickListener(v -> sendSelectLocalFileIntent());
        getActivity().findViewById(R.id.avatar_choose).setOnClickListener(v ->
                showBrowserScreen(BrowserController.BrowserType.DAV_BROWSER));

        getActivity().findViewById(R.id.avatar_delete).setOnClickListener(v ->
                ncApi.deleteAvatar(credentials, ApiUtils.getUrlForTempAvatar(currentUser.getBaseUrl()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<GenericOverall>() {
                            @Override
                            public void onSubscribe(@NotNull Disposable d) {
                            }

                            @Override
                            public void onNext(@NotNull GenericOverall genericOverall) {
                                DisplayUtils.loadAvatarImage(
                                        currentUser,
                                        getActivity().findViewById(R.id.avatar_image),
                                        true);
                            }

                            @Override
                            public void onError(@NotNull Throwable e) {
                                Toast.makeText(getApplicationContext(), "Error", Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onComplete() {

                            }
                        }));

        ViewCompat.setTransitionName(getActivity().findViewById(R.id.avatar_image), "userAvatar.transitionTag");

        ncApi.getUserProfile(credentials, ApiUtils.getUrlForUserProfile(currentUser.getBaseUrl()))
                .retry(3)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<UserProfileOverall>() {
                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }

                    @Override
                    public void onNext(@NotNull UserProfileOverall userProfileOverall) {
                        userInfo = userProfileOverall.getOcs().getData();
                        showUserProfile();
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                        setErrorMessageForMultiList(
                                getActivity().getString(R.string.userinfo_no_info_headline),
                                getActivity().getString(R.string.userinfo_error_text),
                                R.drawable.ic_list_empty_error);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_profile_personal_info_title);
    }

    private void showUserProfile() {
        if (getActivity() == null) {
            return;
        }
        ((TextView) getActivity()
                .findViewById(R.id.userinfo_baseurl))
                .setText(Uri.parse(currentUser.getBaseUrl()).getHost());

        DisplayUtils.loadAvatarImage(currentUser, getActivity().findViewById(R.id.avatar_image), false);

        if (!TextUtils.isEmpty(userInfo.getDisplayName())) {
            ((TextView) getActivity().findViewById(R.id.userinfo_fullName)).setText(userInfo.getDisplayName());
        }

        getActivity().findViewById(R.id.loading_content).setVisibility(View.VISIBLE);

        adapter.setData(createUserInfoDetails(userInfo));

        if (TextUtils.isEmpty(userInfo.getDisplayName()) &&
                TextUtils.isEmpty(userInfo.getPhone()) &&
                TextUtils.isEmpty(userInfo.getEmail()) &&
                TextUtils.isEmpty(userInfo.getAddress()) &&
                TextUtils.isEmpty(userInfo.getTwitter()) &&
                TextUtils.isEmpty(userInfo.getWebsite())) {

            getActivity().findViewById(R.id.userinfo_list).setVisibility(View.GONE);
            getActivity().findViewById(R.id.loading_content).setVisibility(View.GONE);
            getActivity().findViewById(R.id.emptyList).setVisibility(View.VISIBLE);

            setErrorMessageForMultiList(
                    getActivity().getString(R.string.userinfo_no_info_headline),
                    getActivity().getString(R.string.userinfo_no_info_text), R.drawable.ic_user);
        } else {
            getActivity().findViewById(R.id.emptyList).setVisibility(View.GONE);

            getActivity().findViewById(R.id.loading_content).setVisibility(View.GONE);
            getActivity().findViewById(R.id.userinfo_list).setVisibility(View.VISIBLE);
        }

        // show edit button
        if (currentUser.canEditScopes()) {
            ncApi.getEditableUserProfileFields(ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                    ApiUtils.getUrlForUserFields(currentUser.getBaseUrl()))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<UserProfileFieldsOverall>() {
                        @Override
                        public void onSubscribe(@NotNull Disposable d) {
                        }

                        @Override
                        public void onNext(@NotNull UserProfileFieldsOverall userProfileFieldsOverall) {
                            editableFields = userProfileFieldsOverall.getOcs().getData();

                            getActivity().invalidateOptionsMenu();
                            adapter.notifyDataSetChanged();
                        }

                        @Override
                        public void onError(@NotNull Throwable e) {
                            edit = false;
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }
    }

    private void setErrorMessageForMultiList(String headline, String message, @DrawableRes int errorResource) {
        if (getActivity() == null) {
            return;
        }

        ((TextView) getActivity().findViewById(R.id.empty_list_view_headline)).setText(headline);
        ((TextView) getActivity().findViewById(R.id.empty_list_view_text)).setText(message);
        ((ImageView) getActivity().findViewById(R.id.empty_list_icon)).setImageResource(errorResource);

        getActivity().findViewById(R.id.empty_list_icon).setVisibility(View.VISIBLE);
        getActivity().findViewById(R.id.empty_list_view_text).setVisibility(View.VISIBLE);
        getActivity().findViewById(R.id.userinfo_list).setVisibility(View.GONE);
        getActivity().findViewById(R.id.loading_content).setVisibility(View.GONE);
    }

    private List<UserInfoDetailsItem> createUserInfoDetails(UserProfileData userInfo) {
        List<UserInfoDetailsItem> result = new LinkedList<>();

        addToList(result,
                R.drawable.ic_user,
                userInfo.getDisplayName(),
                R.string.user_info_displayname,
                Field.DISPLAYNAME,
                userInfo.getDisplayNameScope());
        addToList(result,
                R.drawable.ic_phone,
                userInfo.getPhone(),
                R.string.user_info_phone,
                Field.PHONE,
                userInfo.getPhoneScope());
        addToList(result, R.drawable.ic_email, userInfo.getEmail(), R.string.user_info_email, Field.EMAIL, userInfo.getEmailScope());
        addToList(result,
                R.drawable.ic_map_marker,
                userInfo.getAddress(),
                R.string.user_info_address,
                Field.ADDRESS,
                userInfo.getAddressScope());
        addToList(result,
                R.drawable.ic_web,
                DisplayUtils.beautifyURL(userInfo.getWebsite()),
                R.string.user_info_website,
                Field.WEBSITE,
                userInfo.getWebsiteScope());
        addToList(
                result,
                R.drawable.ic_twitter,
                DisplayUtils.beautifyTwitterHandle(userInfo.getTwitter()),
                R.string.user_info_twitter,
                Field.TWITTER,
                userInfo.getTwitterScope());

        return result;
    }

    private void addToList(List<UserInfoDetailsItem> info,
                           @DrawableRes int icon,
                           String text,
                           @StringRes int contentDescriptionInt,
                           Field field,
                           Scope scope) {
        info.add(new UserInfoDetailsItem(icon, text, getResources().getString(contentDescriptionInt), field, scope));
    }

    private void save() {
        for (UserInfoDetailsItem item : adapter.displayList) {
            // Text
            if (!item.text.equals(userInfo.getValueByField(item.field))) {
                String credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());

                ncApi.setUserData(
                        credentials,
                        ApiUtils.getUrlForUserData(currentUser.getBaseUrl(), currentUser.getUserId()),
                        item.field.fieldName,
                        item.text)
                        .retry(3)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<GenericOverall>() {
                            @Override
                            public void onSubscribe(@NotNull Disposable d) {
                            }

                            @Override
                            public void onNext(@NotNull GenericOverall userProfileOverall) {
                                Log.d(TAG, "Successfully saved: " + item.text + " as " + item.field);

                                if (item.field == Field.DISPLAYNAME) {
                                    ((TextView) getActivity().findViewById(R.id.userinfo_fullName)).setText(item.text);
                                }
                            }

                            @Override
                            public void onError(@NotNull Throwable e) {
                                item.text = userInfo.getValueByField(item.field);
                                Toast.makeText(getApplicationContext(),
                                        String.format(getResources().getString(R.string.failed_to_save),
                                                item.field),
                                        Toast.LENGTH_LONG).show();
                                adapter.updateFilteredList();
                                adapter.notifyDataSetChanged();
                                Log.e(TAG, "Failed to saved: " + item.text + " as " + item.field, e);
                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }

            // Scope
            if (item.scope != userInfo.getScopeByField(item.field)) {
                saveScope(item, userInfo);
            }

            adapter.updateFilteredList();
        }
    }

    private void sendSelectLocalFileIntent() {
        Intent intent = ImagePicker.Companion.with(getActivity())
                .galleryOnly()
                .crop()
                .cropSquare()
                .compress(1024)
                .maxResultSize(1024, 1024)
                .prepareIntent();

        startActivityForResult(intent, 1);
    }

    private void showBrowserScreen(BrowserController.BrowserType browserType) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(
                BundleKeys.INSTANCE.getKEY_BROWSER_TYPE(),
                Parcels.wrap(BrowserController.BrowserType.class, browserType));
        bundle.putParcelable(
                BundleKeys.INSTANCE.getKEY_USER_ENTITY(),
                Parcels.wrap(UserEntity.class, currentUser));
        bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), "123");
        getRouter().pushController(RouterTransaction.with(new BrowserForAvatarController(bundle, this))
                .pushChangeHandler(new VerticalChangeHandler())
                .popChangeHandler(new VerticalChangeHandler()));
    }

    public void handleAvatar(String remotePath) {
        String uri = currentUser.getBaseUrl() + "/index.php/apps/files/api/v1/thumbnail/512/512/" +
                Uri.encode(remotePath, "/");

        Call<ResponseBody> downloadCall = ncApi.downloadResizedImage(
                ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                uri);

        downloadCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                saveBitmapAndPassToImagePicker(BitmapFactory.decodeStream(response.body().byteStream()));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {

            }
        });
    }

    @SuppressWarnings({"IOI_USE_OF_FILE_STREAM_CONSTRUCTORS"}) // only possible with API26
    private void saveBitmapAndPassToImagePicker(Bitmap bitmap) {
        File file = null;
        try {
            file = File.createTempFile("avatar", "png",
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));


            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (IOException e) {
                Log.e(TAG, "Error compressing bitmap", e);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating temporary avatar image", e);
        }

        if (file == null) {
            // TODO exception
            return;
        }

        Intent intent = ImagePicker.Companion.with(getActivity())
                .fileOnly()
                .crop()
                .cropSquare()
                .compress(1024)
                .maxResultSize(1024, 1024)
                .prepareIntent();

        intent.putExtra(ImagePicker.EXTRA_FILE, file);

        startActivityForResult(intent, 1);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            uploadAvatar(ImagePicker.Companion.getFile(data));
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(getActivity(), ImagePicker.Companion.getError(data), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), "Task Cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAvatar(File file) {
        MultipartBody.Builder builder = new MultipartBody.Builder();
        builder.setType(MultipartBody.FORM);
        builder.addFormDataPart("files[]", file.getName(), RequestBody.create(MediaType.parse("image/*"), file));

        final MultipartBody.Part filePart = MultipartBody.Part.createFormData("files[]", file.getName(),
                RequestBody.create(MediaType.parse("image/jpg"), file));

        // upload file
        ncApi.uploadAvatar(
                ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                ApiUtils.getUrlForTempAvatar(currentUser.getBaseUrl()),
                filePart)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }

                    @Override
                    public void onNext(@NotNull GenericOverall genericOverall) {
                        DisplayUtils.loadAvatarImage(currentUser, getActivity().findViewById(R.id.avatar_image), true);
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                        Toast.makeText(getApplicationContext(), "Error", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    public void saveScope(UserInfoDetailsItem item, UserProfileData userInfo) {
        String credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
        ncApi.setUserData(
                credentials,
                ApiUtils.getUrlForUserData(currentUser.getBaseUrl(), currentUser.getUserId()),
                item.field.getScopeName(),
                item.scope.getName())
                .retry(3)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }

                    @Override
                    public void onNext(@NotNull GenericOverall userProfileOverall) {
                        Log.d(TAG, "Successfully saved: " + item.scope + " as " + item.field);
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                        item.scope = userInfo.getScopeByField(item.field);
                        Log.e(TAG, "Failed to saved: " + item.scope + " as " + item.field, e);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    protected static class UserInfoDetailsItem {
        @DrawableRes
        public int icon;
        public String text;
        public String hint;
        private Field field;
        private Scope scope;

        public UserInfoDetailsItem(@DrawableRes int icon, String text, String hint, Field field, Scope scope) {
            this.icon = icon;
            this.text = text;
            this.hint = hint;
            this.field = field;
            this.scope = scope;
        }
    }

    public static class UserInfoAdapter extends RecyclerView.Adapter<UserInfoAdapter.ViewHolder> {
        protected List<UserInfoDetailsItem> displayList;
        protected List<UserInfoDetailsItem> filteredDisplayList = new LinkedList<>();
        @ColorInt
        protected int mTintColor;
        private final ProfileController controller;

        public static class ViewHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.user_info_detail_container)
            protected View container;
            @BindView(R.id.icon)
            protected ImageView icon;
            @BindView(R.id.user_info_edit_text)
            protected EditText text;
            @BindView(R.id.scope)
            protected ImageView scope;

            public ViewHolder(View itemView) {
                super(itemView);
                ButterKnife.bind(this, itemView);
            }
        }

        public UserInfoAdapter(List<UserInfoDetailsItem> displayList,
                               @ColorInt int tintColor,
                               ProfileController controller) {
            this.displayList = displayList == null ? new LinkedList<>() : displayList;
            mTintColor = tintColor;
            this.controller = controller;
        }

        public void setData(List<UserInfoDetailsItem> displayList) {
            this.displayList = displayList == null ? new LinkedList<>() : displayList;

            updateFilteredList();

            notifyDataSetChanged();
        }

        public void updateFilteredList() {
            filteredDisplayList.clear();

            if (displayList != null) {
                for (UserInfoDetailsItem item : displayList) {
                    if (!TextUtils.isEmpty(item.text)) {
                        filteredDisplayList.add(item);
                    }
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.user_info_details_table_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UserInfoDetailsItem item;

            if (controller.edit) {
                item = displayList.get(position);
            } else {
                item = filteredDisplayList.get(position);
            }


            if (item.scope == null) {
                holder.scope.setVisibility(View.GONE);
            } else {
                holder.scope.setVisibility(View.VISIBLE);

                switch (item.scope) {
                    case PRIVATE:
                    case LOCAL:
                        holder.scope.setImageResource(R.drawable.ic_password);
                        break;
                    case FEDERATED:
                        holder.scope.setImageResource(R.drawable.ic_contacts);
                        break;
                    case PUBLISHED:
                        holder.scope.setImageResource(R.drawable.ic_link);
                        break;
                }
            }

            holder.icon.setImageResource(item.icon);
            holder.text.setText(item.text);
            holder.text.setHint(item.hint);
            holder.text.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (controller.edit) {
                        displayList.get(holder.getAdapterPosition()).text = holder.text.getText().toString();
                    } else {
                        filteredDisplayList.get(holder.getAdapterPosition()).text = holder.text.getText().toString();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            holder.icon.setContentDescription(item.hint);
            DrawableCompat.setTint(holder.icon.getDrawable(), mTintColor);

            if (!TextUtils.isEmpty(item.text) || controller.edit) {
                holder.container.setVisibility(View.VISIBLE);
                if (controller.getActivity() != null) {
                    holder.text.setTextColor(ContextCompat.getColor(
                            controller.getActivity(),
                            R.color.conversation_item_header)
                    );
                }

                if (controller.edit &&
                        controller.editableFields.contains(item.field.toString().toLowerCase(Locale.ROOT))) {
                    holder.text.setEnabled(true);
                    holder.text.setFocusableInTouchMode(true);
                    holder.text.setEnabled(true);
                    holder.text.setCursorVisible(true);
                    holder.text.setBackgroundTintList(ColorStateList.valueOf(mTintColor));
                    holder.scope.setOnClickListener(v -> new ScopeDialog(
                            controller.getActivity(),
                            this,
                            item.field,
                            holder.getAdapterPosition()).show());
                    holder.scope.setAlpha(1.0f);
                } else {
                    holder.text.setEnabled(false);
                    holder.text.setFocusableInTouchMode(false);
                    holder.text.setEnabled(false);
                    holder.text.setCursorVisible(false);
                    holder.text.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                    holder.scope.setOnClickListener(null);
                    holder.scope.setAlpha(0.4f);
                }
            } else {
                holder.container.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            if (controller.edit) {
                return displayList.size();
            } else {
                return filteredDisplayList.size();
            }
        }

        public void updateScope(int position, Scope scope) {
            displayList.get(position).scope = scope;
            notifyDataSetChanged();
        }
    }

    public enum Field {
        EMAIL("email", "emailScope"),
        DISPLAYNAME("displayname", "displaynameScope"),
        PHONE("phone", "phoneScope"),
        ADDRESS("address", "addressScope"),
        WEBSITE("website", "websiteScope"),
        TWITTER("twitter", "twitterScope");

        private final String fieldName;
        private final String scopeName;

        Field(String fieldName, String scopeName) {
            this.fieldName = fieldName;
            this.scopeName = scopeName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getScopeName() {
            return scopeName;
        }
    }
}
