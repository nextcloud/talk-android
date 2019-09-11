/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.Router;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bluelinelabs.conductor.support.RouterPagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.nextcloud.talk.R;
import com.nextcloud.talk.controllers.base.BaseController;

import java.util.Locale;

import butterknife.BindView;

public class MeetingsPagerController  extends BaseController {

    private int[] PAGE_COLORS = new int[]{R.color.colorPrimary, R.color.bg_bottom_sheet, R.color.black, R.color.white, R.color.nc_darkRed};

    @BindView(R.id.tabLayoutMeetings)
    TabLayout tabLayout;
    @BindView(R.id.viewPagerMeetings)
    ViewPager viewPager;

    private RouterPagerAdapter pagerAdapter;

    public  MeetingsPagerController() {
        pagerAdapter = new RouterPagerAdapter(this) {
            @Override
            public  void configureRouter(@NonNull Router router, int position) {
                if (!router.hasRootController()) {

                }
                if(position==0)
                {
                    router.pushController(RouterTransaction.with(new MeetingsListController())
                            .pushChangeHandler(new VerticalChangeHandler())
                            .popChangeHandler(new VerticalChangeHandler())
                            .tag(LockedController.TAG));
                }
            }

            @Override
            public  int getCount() {
                return 2;
            }

            @Override
            public  CharSequence getPageTitle(int position)
            {
                String title="";
                if(position==0)
                {
                    title="Past Meetings";
                }
                else {
                    title="Scheduled Meetings";
                }
                return title;
            }
        };
    }

    @Override
    protected  void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        viewPager.setAdapter(pagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

    }

    @Override
    protected  void onDestroyView(@NonNull View view) {
        viewPager.setAdapter(null);
        super.onDestroyView(view);
    }

    @NonNull
    @Override
    protected  View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_meetings_pager, container, false);
    }

   /* @Override
    protected  String getTitle() {
        return "ViewPager Demo";
    }*/

}