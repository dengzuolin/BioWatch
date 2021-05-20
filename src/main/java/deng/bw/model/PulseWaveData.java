package deng.bw.model;

public class PulseWaveData {
    private int rawVal;
    private int filtedVal;

    public int getRawVal() {
        return rawVal;
    }

    public void setRawVal(int rawVal) {
        this.rawVal = rawVal;
    }

    public int getFiltedVal() {
        return filtedVal;
    }

    public void setFiltedVal(int filtedVal) {
        this.filtedVal = filtedVal;
    }
}
