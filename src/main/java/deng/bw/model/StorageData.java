package deng.bw.model;

import java.util.ArrayList;
import java.util.List;

public class StorageData {
    private Long timestamp;
    private List<PulseWaveData> pulseWaveDatas = new ArrayList<>();
    private Integer ppg = 0;
    private List<Double> gsrs = new ArrayList<>();
    private List<AccData> accDatas = new ArrayList<>();

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public List<PulseWaveData> getPulseWaveDatas() {
        return pulseWaveDatas;
    }

    public void setPulseWaveDatas(List<PulseWaveData> pulseWaveDatas) {
        this.pulseWaveDatas = pulseWaveDatas;
    }

    public Integer getPpg() {
        return ppg;
    }

    public void setPpg(Integer ppg) {
        this.ppg = ppg;
    }

    public List<Double> getGsrs() {
        return gsrs;
    }

    public void setGsrs(List<Double> gsrs) {
        this.gsrs = gsrs;
    }

    public List<AccData> getAccDatas() {
        return accDatas;
    }

    public void setAccDatas(List<AccData> accDatas) {
        this.accDatas = accDatas;
    }
}
