package deng.bw.service;

import com.google.common.collect.EvictingQueue;
import com.google.common.primitives.Bytes;
import deng.bw.model.AccData;
import deng.bw.model.PulseWaveData;
import deng.bw.model.StorageData;
import purejavacomm.SerialPortEvent;
import purejavacomm.SerialPortEventListener;

import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ComEventListener implements SerialPortEventListener {
    private static byte[] PKT_START = new byte[]{0x12,0x34,(byte)0xab,(byte)0xcd};
    private static byte[] PKT_END = new byte[]{(byte)0xcd, (byte)0xab, 0x34, 0x12};
    private static int[] PULSE_WAVE_1_OFFSETS = new int[]{4, 12, 20, 28, 36};
    private static int[] ACC_1_OFFSETS = new int[]{44, 48, 52};
    private static int OHM_1_OFFSET = 56;
    private static int[] PULSE_WAVE_2_OFFSETS = new int[]{60, 68, 76, 84, 92};
    private static int[] ACC_2_OFFSETS = new int[]{100, 104, 108};
    private static int OHM_2_OFFSET = 112;
    private static int BPM_OFFSET = 116;
    private static int BODY_TEMP_OFFSET = 120;

    ComService service;
    InputStream ins;
    EvictingQueue<Byte> _4BytesBuffer = EvictingQueue.create(4);
    List<Byte> dataBuffer = new ArrayList<>();
    boolean canBuffer = false;


    public ComEventListener(ComService service, InputStream ins) {
        this.service = service;
        this.ins = ins;
    }

    private boolean testBytesBingo(EvictingQueue<Byte> byteQueue, byte[] sampleBytes) {
        boolean result = true;
        if (byteQueue == null || sampleBytes == null) {
            result = false;
        } else if (byteQueue.size() != sampleBytes.length) {
            result = false;
        } else {
            byte[] testBytes = Bytes.toArray(byteQueue);
            for (int i = 0; i < testBytes.length; i++) {
                if (testBytes[i] != sampleBytes[i]) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public void serialEvent(SerialPortEvent serialPortEvent) {
        if (serialPortEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE
                && service.serialState.equals(SerialState.BUSY)) {
            int blen = 0;
            try {
                blen = ins.available();
                if (blen > 0) {
                    byte[] buf = new byte[blen];
                    ins.read(buf, 0, blen);
                    for (byte b : buf) {
                        if (canBuffer) {
                            dataBuffer.add(b);
                        }
                        _4BytesBuffer.add(b);
                        if (testBytesBingo(_4BytesBuffer, PKT_START)) {
                            // 接收到数据包头的时候清空当前缓冲，准备接收正式数据
                            canBuffer = true;
                            dataBuffer.clear();
                            for (byte tb : PKT_START) {
                                dataBuffer.add(tb);
                            }
                        } else if (testBytesBingo(_4BytesBuffer, PKT_END)) {
                            StorageData mdata = new StorageData();
                            // 接收到数据包尾时正式采集数据
                            if (dataBuffer.size() == 128) {
                                // 解析第一组数据
                                long timestamp = System.currentTimeMillis();
                                mdata.setTimestamp(timestamp);
                                // 脉搏波
                                for (int o : PULSE_WAVE_1_OFFSETS) {
                                    int rawVal = (dataBuffer.get(o + 2) << 16) | (dataBuffer.get(o + 1) << 8) | (dataBuffer.get(o) & 0xFF);
                                    int filtedVal = (dataBuffer.get(o + 7) << 24) | (dataBuffer.get(o + 6) << 16) | (dataBuffer.get(o + 5) << 8) | (dataBuffer.get(o + 4) & 0xFF);
                                    PulseWaveData pwd = new PulseWaveData();
                                    pwd.setRawVal(rawVal);
                                    pwd.setFiltedVal(filtedVal);
                                    mdata.getPulseWaveDatas().add(pwd);
                                }
                                for (int o : PULSE_WAVE_2_OFFSETS) {
                                    int rawVal = (dataBuffer.get(o + 2) << 16) | (dataBuffer.get(o + 1) << 8) | (dataBuffer.get(o) & 0xFF);
                                    int filtedVal = (dataBuffer.get(o + 7) << 24) | (dataBuffer.get(o + 6) << 16) | (dataBuffer.get(o + 5) << 8) | (dataBuffer.get(o + 4) & 0xFF);
                                    PulseWaveData pwd = new PulseWaveData();
                                    pwd.setRawVal(rawVal);
                                    pwd.setFiltedVal(filtedVal);
                                    mdata.getPulseWaveDatas().add(pwd);
                                }

                                // 三轴加速度
                                int[] accX = new int[6];
                                int[] accY = new int[6];
                                int[] accZ = new int[6];

                                accX[0] = (dataBuffer.get(ACC_1_OFFSETS[0] + 1) << 8) | (dataBuffer.get(ACC_1_OFFSETS[0]) & 0xFF);
                                accY[0] = (dataBuffer.get(ACC_1_OFFSETS[1] + 1) << 8) | (dataBuffer.get(ACC_1_OFFSETS[1]) & 0xFF);
                                accZ[0] = (dataBuffer.get(ACC_1_OFFSETS[2] + 1) << 8) | (dataBuffer.get(ACC_1_OFFSETS[2]) & 0xFF);

                                accX[5] = (dataBuffer.get(ACC_2_OFFSETS[0] + 1) << 8) | (dataBuffer.get(ACC_2_OFFSETS[0]) & 0xFF);
                                accY[5] = (dataBuffer.get(ACC_2_OFFSETS[1] + 1) << 8) | (dataBuffer.get(ACC_2_OFFSETS[1]) & 0xFF);
                                accZ[5] = (dataBuffer.get(ACC_2_OFFSETS[2] + 1) << 8) | (dataBuffer.get(ACC_2_OFFSETS[2]) & 0xFF);

                                // 加速度插值
                                double step = (accX[5] - accX[0])/5.0;
                                accX[1]=(int)(step * 1 + accX[0]*(0.95+Math.random()*0.1));
                                accX[2]=(int)(step * 2 + accX[0]*(0.95+Math.random()*0.1));
                                accX[3]=(int)(step * 3 + accX[0]*(0.95+Math.random()*0.1));
                                accX[4]=(int)(step * 4 + accX[0]*(0.95+Math.random()*0.1));

                                step = (accY[5] - accY[0])/5.0;
                                accY[1]=(int)(step * 1 + accY[0]*(0.95+Math.random()*0.1));
                                accY[2]=(int)(step * 2 + accY[0]*(0.95+Math.random()*0.1));
                                accY[3]=(int)(step * 3 + accY[0]*(0.95+Math.random()*0.1));
                                accY[4]=(int)(step * 4 + accY[0]*(0.95+Math.random()*0.1));

                                step = (accZ[5] - accZ[0])/5.0;
                                accZ[1]=(int)(step * 1 + accZ[0]*(0.95+Math.random()*0.1));
                                accZ[2]=(int)(step * 2 + accZ[0]*(0.95+Math.random()*0.1));
                                accZ[3]=(int)(step * 3 + accZ[0]*(0.95+Math.random()*0.1));
                                accZ[4]=(int)(step * 4 + accZ[0]*(0.95+Math.random()*0.1));

                                for (int i = 0; i < accX.length; i++) {
                                    AccData ad = new AccData(accX[i], accY[i], accZ[i]);
                                    mdata.getAccDatas().add(ad);
                                }

                                // 皮电
                                double[] gsrs = new double[6];
                                int gsrVal1 = ((dataBuffer.get(OHM_1_OFFSET + 1) & 0x3F) << 8) | (dataBuffer.get(OHM_1_OFFSET) & 0xFF);
                                int gsrRes1 = (dataBuffer.get(OHM_1_OFFSET + 1)&0xFF) >> 6;
                                if  (gsrRes1 == 0) {
                                    gsrs[0] = 1 / (gsrVal1*10.0) * 10e6;
                                } else if (gsrRes1 == 1) {
                                    gsrs[0] = 1 / (gsrVal1*100.0) * 10e6;
                                } else if (gsrRes1 == 2) {
                                    gsrs[0] = 1 / (gsrVal1*1000.0) * 10e6;
                                } else if (gsrRes1 == 3) {
                                    gsrs[0] = 1 / (gsrVal1*100000.0) * 10e6;
                                }

                                int gsrVal2 = ((dataBuffer.get(OHM_2_OFFSET + 1) & 0x3F) << 8) | (dataBuffer.get(OHM_2_OFFSET) & 0xFF);
                                int gsrRes2 = (dataBuffer.get(OHM_2_OFFSET + 1) & 0xFF) >> 6;
                                if  (gsrRes2 == 0) {
                                    gsrs[5] = 1 / (gsrVal2*10.0) * 10e6;
                                } else if (gsrRes2 == 1) {
                                    gsrs[5] = 1 / (gsrVal2*100.0) * 10e6;
                                } else if (gsrRes2 == 2) {
                                    gsrs[5] = 1 / (gsrVal2*1000.0) * 10e6;
                                } else if (gsrRes2 == 3) {
                                    gsrs[5] = 1 / (gsrVal2*100000.0) * 10e6;
                                }

                                step = (gsrs[5] - gsrs[0])/5.0;
                                if (gsrs[0] == gsrs[5]) {
                                    gsrs[1] = gsrs[0];
                                    gsrs[2] = gsrs[0];
                                    gsrs[3] = gsrs[0];
                                    gsrs[4] = gsrs[0];
                                } else {
                                    gsrs[1] = (step * 1 + gsrs[0] * (0.95 + Math.random() * 0.1));
                                    gsrs[2] = (step * 1 + gsrs[0] * (0.95 + Math.random() * 0.1));
                                    gsrs[3] = (step * 1 + gsrs[0] * (0.95 + Math.random() * 0.1));
                                    gsrs[4] = (step * 1 + gsrs[0] * (0.95 + Math.random() * 0.1));
                                }
                                for (int i = 0; i < gsrs.length; i++) {
                                    Double gsr = new BigDecimal(gsrs[i]).setScale(4, BigDecimal.ROUND_HALF_UP).doubleValue();
                                    mdata.getGsrs().add(gsr);
                                }

                                // 心率
                                int ppgVal = (dataBuffer.get(BPM_OFFSET + 3) << 8) | (dataBuffer.get(BPM_OFFSET + 2) & 0xFF);
                                mdata.setPpg(ppgVal);
                                service.addToBuffer(mdata);
                            }
                            canBuffer = false;
                            dataBuffer.clear();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
