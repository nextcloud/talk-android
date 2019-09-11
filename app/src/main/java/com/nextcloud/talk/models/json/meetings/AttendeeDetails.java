package com.nextcloud.talk.models.json.meetings;

import javax.annotation.Generated;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

@Generated("com.robohorse.robopojogenerator")
@JsonObject
public class AttendeeDetails{

	@JsonField(name ="schedule-status")
	private String scheduleStatus;

	@JsonField(name ="role")
	private String role;

	@JsonField(name ="partstat")
	private String partstat;

	@JsonField(name ="x-nc-group-id")
	private String xNcGroupId;

	@JsonField(name ="cutype")
	private String cutype;

	@JsonField(name ="cn")
	private String cn;

	@JsonField(name ="rsvp")
	private String rsvp;

	public void setScheduleStatus(String scheduleStatus){
		this.scheduleStatus = scheduleStatus;
	}

	public String getScheduleStatus(){
		return scheduleStatus;
	}

	public void setRole(String role){
		this.role = role;
	}

	public String getRole(){
		return role;
	}

	public void setPartstat(String partstat){
		this.partstat = partstat;
	}

	public String getPartstat(){
		return partstat;
	}

	public void setXNcGroupId(String xNcGroupId){
		this.xNcGroupId = xNcGroupId;
	}

	public String getXNcGroupId(){
		return xNcGroupId;
	}

	public void setCutype(String cutype){
		this.cutype = cutype;
	}

	public String getCutype(){
		return cutype;
	}

	public void setCn(String cn){
		this.cn = cn;
	}

	public String getCn(){
		return cn;
	}

	public void setRsvp(String rsvp){
		this.rsvp = rsvp;
	}

	public String getRsvp(){
		return rsvp;
	}
}