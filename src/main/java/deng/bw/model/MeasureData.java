package deng.bw.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MeasureData {
    private String recordId;
    private Date dataTime;
    private int sectionIndex;
    private PulseWaveData pulesWave = new PulseWaveData();
    private Integer accValX;
    private Integer accValY;
    private Integer accValZ;
    private Integer ohmVal;
    private Integer ohmQuantum;
    private Integer bpmVal;
    private Float bodyTemp;

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public Date getDataTime() {
        return dataTime;
    }

    public void setDataTime(Date dataTime) {
        this.dataTime = dataTime;
    }

    public int getSectionIndex() {
        return sectionIndex;
    }

    public void setSectionIndex(int sectionIndex) {
        this.sectionIndex = sectionIndex;
    }

    public PulseWaveData getPulesWave() {
        return pulesWave;
    }

    public void setPulesWave(PulseWaveData pulesWave) {
        this.pulesWave = pulesWave;
    }

    public Integer getOhmVal() {
        return ohmVal;
    }

    public void setOhmVal(Integer ohmVal) {
        this.ohmVal = ohmVal;
    }

    public Integer getOhmQuantum() {
        return ohmQuantum;
    }

    public void setOhmQuantum(Integer ohmQuantum) {
        this.ohmQuantum = ohmQuantum;
    }

    public Integer getAccValX() {
        return accValX;
    }

    public void setAccValX(Integer accValX) {
        this.accValX = accValX;
    }

    public Integer getAccValY() {
        return accValY;
    }

    public void setAccValY(Integer accValY) {
        this.accValY = accValY;
    }

    public Integer getAccValZ() {
        return accValZ;
    }

    public void setAccValZ(Integer accValZ) {
        this.accValZ = accValZ;
    }

    public Integer getBpmVal() {
        return bpmVal;
    }

    public void setBpmVal(Integer bpmVal) {
        this.bpmVal = bpmVal;
    }

    public Float getBodyTemp() {
        return bodyTemp;
    }

    public void setBodyTemp(Float bodyTemp) {
        this.bodyTemp = bodyTemp;
    }
}
