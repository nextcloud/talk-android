/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.adapters.items;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.emoji.widget.EmojiTextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;

import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.meetings.MeetingsReponse;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DateUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VTimeZone;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

import static com.nextcloud.talk.models.json.conversations.Conversation.ConversationType.ROOM_GROUP_CALL;
import static com.nextcloud.talk.models.json.conversations.Conversation.ConversationType.ROOM_PUBLIC_CALL;
import static com.nextcloud.talk.models.json.conversations.Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL;

public class MeetingItems extends AbstractFlexibleItem<MeetingItems.ConversationItemViewHolder> implements
        IFilterable<String> {



    private MeetingsReponse meeting;
    private UserEntity userEntity;

    public MeetingItems(MeetingsReponse meeting, UserEntity userEntity) {
        this.meeting = meeting;
        this.userEntity = userEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MeetingItems) {
            MeetingItems inItem = (MeetingItems) o;
            return meeting.equals(inItem.getModel());
        }
        return false;
    }

    public MeetingsReponse getModel() {
        return meeting;
    }

    @Override
    public int hashCode() {
        return meeting.hashCode();
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_meeting;
    }

    @Override
    public ConversationItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ConversationItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ConversationItemViewHolder holder, int position, List<Object> payloads) {
        Context context = NextcloudTalkApplication.Companion.getSharedApplication().getApplicationContext();


        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.meetingTitle, meeting.getTitle(),
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.Companion.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.meetingTitle.setText(meeting.getTitle());
        }



        if (meeting.isOwner()) {
            holder.meetingHostLinearLayout.setVisibility(View.VISIBLE);
            holder.meetingHostTextView.setText("Host");
        } else {
            holder.meetingHostLinearLayout.setVisibility(View.GONE);
        }

        holder.meetingDateTextView.setText(DateUtils.INSTANCE.getDateTimeStringFromTimestamp(meeting.getStart(),"dd MMMM yyyy",""));
        holder.meetingTimeTextView.setText(DateUtils.INSTANCE.getDateTimeStringFromTimestamp(meeting.getStart(),"HH:mm",""));

        if(meeting.isJsonMemberPublic())
        {
            holder.meetingType.setText(context.getString(R.string.str_public));
        }
        else {
            holder.meetingType.setText(context.getString(R.string.str_private));
        }

        if(meeting.getDescription().isEmpty())
        {
            holder.meetingDescription.setVisibility(View.GONE);
        }
        else {
            holder.meetingDescription.setVisibility(View.VISIBLE);
            holder.meetingDescription.setText(meeting.getDescription());

        }
        holder.meetingFrequencyTextView.setVisibility(View.GONE);

        holder.meetingID.setText(context.getResources().getString(R.string.str_meeting_id)+" "+meeting.getMeetingid());


        StringReader sin = new StringReader(meeting.getVcalendar());

        CalendarBuilder builder = new CalendarBuilder();
        Calendar cal = null;
        try {
            cal = builder.build(sin);

            VTimeZone component= (VTimeZone) cal.getComponents().getComponent("VTIMEZONE");
            Property rrule=component.getObservances().getComponent("STANDARD").getProperties().getProperty("RRULE");
            String rule=rrule.getValue().toString();
            if(getTextForFrequency(rule).equalsIgnoreCase(""))
            {
                holder.meetingFrequencyTextView.setVisibility(View.GONE);
            }
            else {
                holder.meetingFrequencyTextView.setVisibility(View.VISIBLE);
                holder.meetingFrequencyTextView.setText(getTextForFrequency(rule));
            }

            //FREQ=YEARLY;BYMONTH=4;BYDAY=1SU
        } catch (IOException e) {
            Log.d("calendar", "io exception" + e.getLocalizedMessage());
        } catch (ParserException e) {
            Log.d("calendar", "parser exception" + e.getLocalizedMessage());
        }

    }

    private String getTextForFrequency(String rule)
    {
        String[] splitted=rule.split(";");
        String frequency = "",interval="0";

        String finalText="";
        for (int i=0;i<splitted.length;i++)
        {
            if(splitted[i].contains("FREQ"))
            {
                frequency=(splitted[i].split("="))[1];
            }

            if(splitted[i].contains("INTERVAL"))
            {
                interval=((splitted[i].split("="))[1]);
            }
        }

        if(interval.equalsIgnoreCase("0"))
            return "";
        if(frequency.equalsIgnoreCase("DAILY"))
        {
            if(interval.equalsIgnoreCase("1"))
            {
                finalText="Every Day";
            }
            else {
                finalText="Every "+interval+" day";
            }
        }
        else if(frequency.equalsIgnoreCase("WEEKLY"))
        {
            if(interval.equalsIgnoreCase("1"))
            {
                finalText="Every Week";
            }
            else {
                finalText="Every "+interval+" Weeks";
            }
        }
        else if(frequency.equalsIgnoreCase("MONTHLY"))
        {
            if(interval.equalsIgnoreCase("1"))
            {
                finalText="Every Month";
            }
            else {
                finalText="Every "+interval+" Months";
            }
        }
        else if(frequency.equalsIgnoreCase("YEARLY"))
        {
            if(interval.equalsIgnoreCase("1"))
            {
                finalText="Every Year";
            }
            else {
                finalText="Every "+interval+" Years";
            }
        }
        return finalText;

    }

    @Override
    public boolean filter(String constraint) {
        return meeting.getTitle() != null &&
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(meeting.getTitle()).find();
    }

    static class ConversationItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.meetingTitle)
        TextView meetingTitle;
        @BindView(R.id.meetingType)
        TextView meetingType;
        @BindView(R.id.meetingID)
        TextView meetingID;
        @BindView(R.id.meetingTitleRelativeLayout)
        RelativeLayout meetingTitleRelativeLayout;
        @BindView(R.id.passwordProtectedRoomImageView)
        ImageView passwordProtectedRoomImageView;
        @BindView(R.id.meetingDateTextView)
        TextView meetingDateTextView;
        @BindView(R.id.meetingDateLinearLayout)
        LinearLayout meetingDateLinearLayout;
        @BindView(R.id.meetingTimeImageView)
        ImageView meetingTimeImageView;
        @BindView(R.id.meetingTimeTextView)
        TextView meetingTimeTextView;
        @BindView(R.id.meetingTimeLinearLayout)
        LinearLayout meetingTimeLinearLayout;
        @BindView(R.id.meetingHostImageView)
        ImageView meetingHostImageView;
        @BindView(R.id.meetingHostTextView)
        TextView meetingHostTextView;
        @BindView(R.id.meetingHostLinearLayout)
        LinearLayout meetingHostLinearLayout;
        @BindView(R.id.meetingFrequencyTextView)
        TextView meetingFrequencyTextView;
        @BindView(R.id.meetingDescription)
        TextView meetingDescription;
        ConversationItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
