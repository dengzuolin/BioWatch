package deng.bw.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import deng.bw.main.Controller;
import deng.bw.model.*;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import purejavacomm.*;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Component
@ComponentScan
public class ComService {
    private static Logger logger = LoggerFactory.getLogger(ComService.class);

    private static long SERIAL_INIT_TIMEOUT = 2000;

    protected static byte WB_CMD_HEADER = (byte)0x5A;

    /***************** 发送命令ID **********************/
    protected static byte WB_CMD_READ_ID = (byte)0xA0;        // 腕表ID号
    protected static byte WB_CMD_READ_INTERVAL = (byte)0xA2;  // 腕表采样间隔和采样长度
    protected static byte WB_CMD_READ_BATTERY = (byte)0xA4;   // 电池电量信息
    protected static byte WB_CMD_READ_FRQ = (byte)0xB5;       // 正常模式下的存储数据格式及采样频率
    protected static byte WB_CMD_READ_TIME = (byte)0xA7;      // 标准时间
    protected static byte WB_CMD_READ_MODE = (byte)0xB3;      // 工作模式（正常模式、精简工作模式、待机模式）
    protected static byte[] WB_CMD_START_RECORD = new byte[]{0x5A,0x08,(byte)0xAB,0x01,0x00,0x00,0x00,0x0E};
    protected static byte[] WB_CMD_STOP_RECORD = new byte[]{0x5A,0x08,(byte)0xAB,0x00,0x00,0x00,0x00,0x0D};
    protected static String CSV_TITLE = "origin_pulseWave_0,origin_pulseWave_1,origin_pulseWave_2,origin_pulseWave_3,origin_pulseWave_4,origin_pulseWave_5,origin_pulseWave_6,origin_pulseWave_7,origin_pulseWave_8,origin_pulseWave_9,filter_pulseWave_0,filter_pulseWave_1,filter_pulseWave_2,filter_pulseWave_3,filter_pulseWave_4,filter_pulseWave_5,filter_pulseWave_6,filter_pulseWave_7,filter_pulseWave_8,filter_pulseWave_9,PPG,GSR0,GSR1,GSR2,GSR3,GSR4,GSR5,GSR6,GSR7,GSR8,GSR9,Motion_dataX0,Motion_dataX1,Motion_dataX2,Motion_dataX3,Motion_dataX4,Motion_dataX5,Motion_dataX6,Motion_dataX7,Motion_dataX8,Motion_dataX9,Motion_dataY0,Motion_dataY1,Motion_dataY2,Motion_dataY3,Motion_dataY4,Motion_dataY5,Motion_dataY6,Motion_dataY7,Motion_dataY8,Motion_dataY9,Motion_dataZ0,Motion_dataZ1,Motion_dataZ2,Motion_dataZ3,Motion_dataZ4,Motion_dataZ5,Motion_dataZ6,Motion_dataZ7,Motion_dataZ8,Motion_dataZ9,data_time";

    protected static int WB_RW_CMD_OFFSET = 64;

    public static int CHART_MAX = 375;

    SerialPort serialPort;

    Controller controller;

    @Value("${deng.bw.service.ComService.serialPortBaudRate}")
    int serialPortBaudRate = 9600;
    @Value("${deng.bw.service.ComService.udpUpPort}")
    int udpUpPort = 9600;

    SerialState serialState = SerialState.STANDBY;

    byte[] bufferedCommand = null;
    boolean watchConnected = false;

    boolean serialPortEnabled = true;
    boolean serialPortDisabled = false;

    LinkedBlockingDeque<StorageData> dataBuffer = new LinkedBlockingDeque<>();
    LinkedBlockingDeque<StorageData> dbSaveBuffer = new LinkedBlockingDeque<>();

    LineChart pulseWaveChart;
    LineChart bpmChart;
    LineChart ohmChart;
    LineChart accChart;

    Label bpmLabel;
    Label ohmLabel;
    Label accXLabel;
    Label accYLabel;
    Label accZLabel;
    Label accSqtLabel;

    int x1 = 0;
    int x2 = 0;
    int x3 = 0;
    int x4 = 0;

    String recordId = null;
    BufferedWriter csvWriter = null;
    BufferedWriter csvWriter2 = null;

    JdbcTemplate jdbcTemplate;

    boolean saveRunningFlag = false;

    int[] lastAcc = null;
    Double lastGsrs = null;

    public ComService(Controller controller) {
        this.controller = controller;
    }

    public List<String> getSerialPorts() {
        List<String> result = new ArrayList<>();
        Enumeration ports = CommPortIdentifier.getPortIdentifiers();
        while (ports.hasMoreElements()) {
            CommPortIdentifier cpi = (CommPortIdentifier)ports.nextElement();
            result.add(cpi.getName());
        }
        return result;
    }

    public boolean initSerialPort(String portId) {
        boolean result = false;
        if (serialPort != null) {
            // 设置串口关闭标志位，等待串口命令循环结束
            serialPortEnabled = false;
            long timeMil = System.currentTimeMillis();
            while (1 == 1) {
                if (serialPortDisabled) {
                    break;
                }
                if (System.currentTimeMillis() - timeMil > SERIAL_INIT_TIMEOUT) {
                    break;
                }
            }
            if (serialPortDisabled) {
                // 关闭串口
                serialPort.close();
                serialPort = null;
                try {
                    Thread.sleep(100L);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (serialPort == null) {
            try {
                if (portId != null && !portId.equals("")) {
                    CommPortIdentifier portid = CommPortIdentifier.getPortIdentifier(portId);
                    if (portid != null) {
                        serialPort = (SerialPort) portid.open(portId, 3);
                        serialPort.setSerialPortParams(serialPortBaudRate,SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                        serialPort.notifyOnDataAvailable(true);
                        serialPort.notifyOnOutputEmpty(true);
                        serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
                        final InputStream ins = serialPort.getInputStream();
                        ComEventListener listener = new ComEventListener(this, ins);
                        serialPort.addEventListener(listener);
                        serialState = SerialState.STANDBY;
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                result = true;
                disconnectWatch();
                readSerialSync(100);
                watchConnected = false;
            } catch (UnsupportedCommOperationException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchPortException e) {
                e.printStackTrace();
            } catch (PortInUseException e) {
                e.printStackTrace();
            } catch (TooManyListenersException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public boolean isSerialOpened() {
        if (serialPort != null) {
            return true;
        } else {
            return false;
        }
    }

    public boolean closeSerialPort() {
        boolean result = false;
        if (serialPort != null) {
            // 设置串口关闭标志位，等待串口命令循环结束
            serialPortEnabled = false;
            long timeMil = System.currentTimeMillis();
            while (1 == 1) {
                if (serialPortDisabled) {
                    break;
                }
                if (System.currentTimeMillis() - timeMil > SERIAL_INIT_TIMEOUT) {
                    break;
                }
            }
            if (serialPortDisabled) {
                try {
                    // 关闭串口
                    serialPort.close();
                    serialState = SerialState.STANDBY;
                    Thread.sleep(100L);
                    result = true;
                    watchConnected = false;
                    serialState = SerialState.STANDBY;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    serialPort = null;
                }
            }
        }
        return result;
    }

    private byte[] readSerialSync(long timeOut) {
        byte[] result = null;
        List<Byte> recvBytes = new ArrayList<>();
        try {
            if (serialPort != null) {
                int blen = serialPort.getInputStream().available();
                long timeMil = System.currentTimeMillis();
                while (blen > 0 || System.currentTimeMillis() - timeMil < timeOut) {
                    if (blen > 0) {
                        byte[] buffer = new byte[blen];
                        serialPort.getInputStream().read(buffer, 0, blen);
                        for (byte b : buffer) {
                            recvBytes.add(b);
                        }
                    }
                    Thread.sleep(50);
                    blen = serialPort.getInputStream().available();
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        result = new byte[recvBytes.size()];
        for (int i = 0; i < recvBytes.size(); i++) {
           result[i] = recvBytes.get(i);
        }
        return result;
    }

    public List<String> scanWatch() {
        List<String> result = new ArrayList<>();
        if (serialPort != null && serialState.equals(SerialState.STANDBY)) {
            try {
                Pattern p = Pattern.compile("(OK\\+DIS[0-9]|OK\\+DISC):([^:]{12}):([^:]+):([^:]+)");
                serialPort.getOutputStream().write("AT+DISC?".getBytes());
                serialPort.getOutputStream().flush();
                Thread.sleep(500);
                byte[] serialResult = readSerialSync(5000);
                if (serialResult.length > 0) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(serialResult)));
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        logger.info("DISC: " + line);
                        Matcher m = p.matcher(line);
                        if (m.find()) {
                            String mac = m.group(2);
                            String devName = m.group(4);
                            if (!devName.equals("MKR")) {
                                result.add(mac + ":" + devName);
                            }
                        }
                    }
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public boolean connectWatch(String mac) {
        boolean result = false;
        if (mac != null && !mac.equals("") && serialPort != null && serialState.equals(SerialState.STANDBY)) {
            try {
                Pattern p = Pattern.compile("AT+CON(.*)");
                serialPort.getOutputStream().write(("AT+CON" + mac).getBytes());
                serialPort.getOutputStream().flush();
                Thread.sleep(500);
                byte[] serialResult = readSerialSync(5000);
                if (serialResult.length > 0) {
                    String sr = new String(serialResult);
                    if (sr.contains("CONN\r\n")) {
                        result = true;
                        watchConnected = true;
                        serialState = SerialState.STANDBY;
                    }
                }
                serialPort.getOutputStream().write(WB_CMD_STOP_RECORD);
                serialPort.getOutputStream().flush();
                readSerialSync(2000);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private void sendReadCmdPacket(byte cmdId) {
        if (serialPort != null) {
            try {
                byte[] cmd = new byte[8];
                cmd[0] = WB_CMD_HEADER;
                cmd[1] = (byte)8;
                cmd[2] = cmdId;
                byte sum = 0;
                for (int i = 0; i < cmd.length - 1; i++) {
                    sum = (byte)(sum + cmd[i]);
                }
                cmd[7] = sum;

                serialPort.getOutputStream().write(cmd);
                serialPort.getOutputStream().flush();;
                Thread.sleep(50);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public List<WatchConfig> getWatchConfig() {
        List<WatchConfig> result = new ArrayList<>();
        if (serialPort != null && watchConnected && serialState.equals(SerialState.STANDBY)) {
            try {
                byte[] recvData;
                sendReadCmdPacket(WB_CMD_READ_ID);
                recvData = readSerialSync(100);
                if (recvData.length >= 8) {
                    int watchId = (recvData[3] << 8)| (recvData[4]&0xFF);
                    result.add(new WatchConfig("ID", String.valueOf(watchId)));
                }
                sendReadCmdPacket(WB_CMD_READ_INTERVAL);
                recvData = readSerialSync(100);
                if (recvData.length >= 8) {
                    int sampleInterval = (recvData[4] << 8)| (recvData[3]&0xFF);
                    int sampleLen = (recvData[6] << 8)| (recvData[5]&0xFF);
                    result.add(new WatchConfig("采样间隔", String.valueOf(sampleInterval)));
                    result.add(new WatchConfig("采样长度", String.valueOf(sampleLen)));
                }
                sendReadCmdPacket(WB_CMD_READ_BATTERY);
                recvData = readSerialSync(100);
                if (recvData.length >= 8) {
                    int battery = (recvData[5] << 8)| (recvData[6]&0xFF);
                    result.add(new WatchConfig("电池电量", String.valueOf(battery)));
                }
                sendReadCmdPacket(WB_CMD_READ_FRQ);
                recvData = readSerialSync(100);
                if (recvData.length >= 8) {
                    int formatIdx = recvData[3]&0xFF;
                    int bpmHz = recvData[4]&0xFF;
                    int accHz = recvData[5]&0xFF;
                    int ohmHz = recvData[6]&0xFF;
                    result.add(new WatchConfig("数据格式", String.valueOf(formatIdx)));
                    result.add(new WatchConfig("脉搏波降采帧率", String.valueOf(bpmHz)+"Hz"));
                    result.add(new WatchConfig("运动采集帧率", String.valueOf(accHz)+"Hz"));
                    result.add(new WatchConfig("皮电采样频率", String.valueOf(ohmHz)+"Hz"));
                }
                sendReadCmdPacket(WB_CMD_READ_MODE);
                recvData = readSerialSync(100);
                if (recvData.length >= 8) {
                    int mode = recvData[3]&0xFF;
                    if (mode == 0) {
                        result.add(new WatchConfig("工作模式", "正常模式"));
                    } else if (mode == 1) {
                        result.add(new WatchConfig("工作模式", "精简模式"));
                    } else if (mode == 2){
                        result.add(new WatchConfig("工作模式", "睡眠模式"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public boolean disconnectWatch() {
        boolean result = false;
        if (serialPort != null) {
            try {
                serialPort.getOutputStream().write(("AT").getBytes());
                serialPort.getOutputStream().flush();
                Thread.sleep(100);
                result = true;
                watchConnected = false;
                serialState = SerialState.STANDBY;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public boolean isWatchConnected() {
        return this.watchConnected;
    }


    public boolean startRecord() {
        boolean result = false;
        if (serialPort != null
                && serialState.equals(SerialState.STANDBY)) {
            try {
                this.dbSaveBuffer.clear();
                this.dataBuffer.clear();
                this.lastAcc = null;
                this.lastGsrs = null;

                /*+++++++++++++++++++*/
//                this.recordId = UUID.randomUUID().toString();
//                jdbcTemplate.update("INSERT INTO RECORD_LOG(REC_ID, TIME_START) VALUES(?,?)", this.recordId, new Date());
                /*-------------------*/
                /*+++++++++++++++++*/
                SimpleDateFormat dfmt = new SimpleDateFormat("yyyy-MM-dd_HHmm");
                String csvFileName = "export_" + dfmt.format(new Date()) + ".csv";
                File outDir = new File("out");
                if (!outDir.exists()) {
                    outDir.mkdir();
                }
                File outFile = new File("out" + File.separator + csvFileName);
                this.csvWriter = new BufferedWriter(new FileWriter(outFile));
                this.csvWriter.write(CSV_TITLE + "\n");

                String csvFileName2 = "export_" + dfmt.format(new Date()) + "-2.csv";
                File outFile2 = new File("out" + File.separator + csvFileName2);
                this.csvWriter2 = new BufferedWriter(new FileWriter(outFile2));
                /*-----------------*/

                serialPort.getOutputStream().write(WB_CMD_START_RECORD);
                serialPort.getOutputStream().flush();
                serialState = SerialState.BUSY;
                Thread.sleep(100);
                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public boolean stopRecord() {
        boolean result = false;
        if (serialPort != null
                && serialState.equals(SerialState.BUSY)) {
            try {
                /*+++++++++++++++++*/
//                jdbcTemplate.update("UPDATE RECORD_LOG SET TIME_END = ? WHERE REC_ID = ?", new Date(), this.recordId);
//                this.recordId = null;
                /*-----------------*/

                // 等待保存的过程结束
                long timeMil = System.currentTimeMillis();
                long timeOut = 3000;
                while ((System.currentTimeMillis() - timeMil < timeOut) && this.saveRunningFlag) {}

                /*+++++++++++++++++*/
                try {
                    this.csvWriter.flush();
                    this.csvWriter.close();
                    this.csvWriter = null;

                    this.csvWriter2.flush();
                    this.csvWriter2.close();
                    this.csvWriter2 = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                /*-----------------*/

                serialPort.getOutputStream().write(WB_CMD_STOP_RECORD);
                serialPort.getOutputStream().flush();
                Thread.sleep(100);
                serialState = SerialState.STANDBY;

                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public void setCharts(LineChart pulseWaveChart
            , LineChart bpmChart
            , LineChart ohmChart
            , LineChart accChart) {
        this.pulseWaveChart = pulseWaveChart;
        this.bpmChart = bpmChart;
        this.ohmChart = ohmChart;
        this.accChart = accChart;
    }

    public void setLabels(Label bpmLabel
            , Label ohmLabel
            , Label accXLabel
            , Label accYLabel
            , Label accZLabel
            , Label accSqtLabel) {
        this.bpmLabel = bpmLabel;
        this.ohmLabel = ohmLabel;
        this.accXLabel = accXLabel;
        this.accYLabel = accYLabel;
        this.accZLabel = accZLabel;
        this.accSqtLabel = accSqtLabel;
    }

    @Scheduled(fixedDelay = 500)
    public void updateChart() {
        if (serialState.equals(SerialState.BUSY)) {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    StorageData d;
                    while ((d = dataBuffer.poll()) != null) {
                        XYChart.Series<String, Integer> pulse = (XYChart.Series)pulseWaveChart.getData().get(0);
                        XYChart.Series<String, Integer> bpm = (XYChart.Series)bpmChart.getData().get(0);
                        XYChart.Series<String, Double> ohm = (XYChart.Series)ohmChart.getData().get(0);
                        XYChart.Series<String, Integer> accX = (XYChart.Series)accChart.getData().get(0);
                        XYChart.Series<String, Integer> accY = (XYChart.Series)accChart.getData().get(1);
                        XYChart.Series<String, Integer> accZ = (XYChart.Series)accChart.getData().get(2);

                        for (PulseWaveData pd : d.getPulseWaveDatas()) {
                            if (pulse.getData().size() == CHART_MAX) {
                                pulse.getData().remove(0);
                            }
                            pulse.getData().add(new XYChart.Data<String, Integer>(String.valueOf(x1), pd.getFiltedVal()));
                            x1++;
                        }

                        if (bpm.getData().size() == CHART_MAX) {
                            bpm.getData().remove(0);
                        }
                        bpm.getData().add(new XYChart.Data<String, Integer>(String.valueOf(x2), d.getPpg()));
                        bpmLabel.setText(String.valueOf(d.getPpg()));
                        x2++;

                        for (Double gsr : d.getGsrs()) {
                            if (ohm.getData().size() == CHART_MAX) {
                                ohm.getData().remove(0);
                            }
                            ohm.getData().add(new XYChart.Data<String, Double>(String.valueOf(x3), gsr));
                            ohmLabel.setText(new DecimalFormat("0.0000").format(gsr));
                            x3++;
                        }

                        List<Double> sqts = new ArrayList<>();
                        for (AccData acc : d.getAccDatas()) {
                            if (accX.getData().size() == CHART_MAX) {
                                accX.getData().remove(0);
                                accY.getData().remove(0);
                                accZ.getData().remove(0);
                            }
                            accX.getData().add(new XYChart.Data<String, Integer>(String.valueOf(x4), acc.getX()));
                            accY.getData().add(new XYChart.Data<String, Integer>(String.valueOf(x4), acc.getY()));
                            accZ.getData().add(new XYChart.Data<String, Integer>(String.valueOf(x4), acc.getZ()));
                            accXLabel.setText(String.valueOf(acc.getX()));
                            accYLabel.setText(String.valueOf(acc.getY()));
                            accZLabel.setText(String.valueOf(acc.getZ()));
                            double sqt = Math.sqrt(Math.pow(acc.getX(),2) + Math.pow(acc.getY(),2) + Math.pow(acc.getZ(),2));
                            sqt = sqt/4200*9.86;
                            sqts.add(sqt);
                            x4++;
                        }
                        accSqtLabel.setText(new DecimalFormat("#.00").format((sqts.get(0) + sqts.get(1))/2));
                    }
                }
            });
        }
    }

    public SerialState getSerialState() {
        return this.serialState;
    }

    public void addToBuffer(StorageData data) {
        dataBuffer.add(data);
        dbSaveBuffer.add(data);
    }

//    @Transactional
//    @Scheduled(fixedDelay = 1000)
    public void saveDataToDb() {
        if (this.dbSaveBuffer.size() > 0 && this.recordId != null) {
            saveRunningFlag = true;
            try {
                int qlen = this.dbSaveBuffer.size();
                int c = 1;
                StringBuilder pwSql = new StringBuilder("INSERT INTO PULSE_WAVE(REC_ID, DATA_TIME, IDX, RAW_VAL, FILTED_VAL) VALUES");
                StringBuilder gsrSql = new StringBuilder("INSERT INTO GSR_DATA(REC_ID, DATA_TIME, IDX, GSR_VAL) VALUES");
                StringBuilder accSql = new StringBuilder("INSERT INTO ACC_DATA(REC_ID, DATA_TIME, IDX, X, Y, Z) VALUES");
                StringBuilder ppgSql = new StringBuilder("INSERT INTO PPG(REC_ID, DATA_TIME, PPG) VALUES");
                while (c <= qlen) {
                    StorageData sdata =  this.dbSaveBuffer.poll();
                    try {
                        if (sdata != null && this.recordId != null) {
                            for (int i = 0; i < sdata.getPulseWaveDatas().size(); i++) {
                                PulseWaveData pwd = sdata.getPulseWaveDatas().get(i);
                                pwSql.append("('" + this.recordId + "',").append(sdata.getTimestamp()).append(",").append(i).append(",").append(pwd.getRawVal()).append(",").append(pwd.getFiltedVal()).append(")\n");
                            }
                            for (int i = 0; i < sdata.getGsrs().size(); i++) {
                                Double gsr = sdata.getGsrs().get(i);
                                gsrSql.append("('" + this.recordId + "',").append(sdata.getTimestamp()).append(",").append(i).append(",").append(gsr).append(")\n");
                            }
                            for (int i = 0; i < sdata.getAccDatas().size(); i++) {
                                AccData ad = sdata.getAccDatas().get(i);
                                accSql.append("('" + this.recordId + "',").append(sdata.getTimestamp()).append(",").append(i).append(",").append(ad.getX()).append(",").append(ad.getY()).append(",").append(ad.getZ()).append(")\n");
                            }
                            ppgSql.append("('" + this.recordId + "',").append(sdata.getTimestamp()).append(",").append(sdata.getPpg()).append(")\n");
                        }
                    } finally {
                        c++;
                    }
                }
                jdbcTemplate.update(pwSql.toString());
                jdbcTemplate.update(gsrSql.toString());
                jdbcTemplate.update(accSql.toString());
                jdbcTemplate.update(ppgSql.toString());
            } finally {
                saveRunningFlag = false;
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void saveDataToFile() {
        if (this.dbSaveBuffer.size() > 0 && this.csvWriter != null) {
            saveRunningFlag = true;
            DatagramSocket socket = null;
            try {
                InetAddress iAddr = InetAddress.getByName("localhost");
                int qlen = this.dbSaveBuffer.size();
                int c = 1;
                Gson gson = new GsonBuilder().create();
                socket = new DatagramSocket();

                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss S");
                SimpleDateFormat fmt2 = new SimpleDateFormat("HH:mm:ss");
                while (c <= qlen) {
                    StorageData sdata =  this.dbSaveBuffer.poll();
                    StringBuilder line = new StringBuilder();
                    try {
                        if (sdata != null && this.csvWriter != null) {
                            // 脉搏波
                            for (int i = 0; i < sdata.getPulseWaveDatas().size(); i++) {
                                PulseWaveData pwd = sdata.getPulseWaveDatas().get(i);
                                if (i > 0) {
                                    line.append(",");
                                }
                                line.append(pwd.getRawVal());
                            }
                            for (int i = 0; i < sdata.getPulseWaveDatas().size(); i++) {
                                PulseWaveData pwd = sdata.getPulseWaveDatas().get(i);
                                line.append(",");
                                line.append(pwd.getFiltedVal());
                            }

                            // 心率
                            line.append(",");
                            if (sdata.getPpg() != null) {
                                line.append(sdata.getPpg());
                            }

                            // 皮电
                            for (int i = 0; i < sdata.getGsrs().size(); i++) {
                                Double gsr = sdata.getGsrs().get(i);
                                line.append(",");
                                line.append((new BigDecimal(gsr)).setScale(4, BigDecimal.ROUND_HALF_UP).doubleValue());
                            }

                            // 加速度
                            for (int i = 0; i < sdata.getAccDatas().size(); i++) {
                                AccData ad = sdata.getAccDatas().get(i);
                                line.append(",");
                                line.append(ad.getX());

                                /***** 写入第二个文件 *********/
                                int ppg = sdata.getPpg();
                                double gsr = sdata.getGsrs().get(i);
                                double sqt = Math.sqrt(Math.pow(ad.getX(),2) + Math.pow(ad.getY(),2) + Math.pow(ad.getZ(),2));
                                sqt = sqt/4200*9.86;
                                try {
                                    this.csvWriter2.write(fmt2.format(new Date(sdata.getTimestamp()))
                                            + "," + String.valueOf(ppg)
                                            + "," + String.valueOf((new BigDecimal(gsr)).setScale(4, BigDecimal.ROUND_HALF_UP).doubleValue())
                                            + "," + String.valueOf((new BigDecimal(sqt)).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue())
                                            + "\n");
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                /****************************/
                            }
                            for (int i = 0; i < sdata.getAccDatas().size(); i++) {
                                AccData ad = sdata.getAccDatas().get(i);
                                line.append(",");
                                line.append(ad.getY());
                            }
                            for (int i = 0; i < sdata.getAccDatas().size(); i++) {
                                AccData ad = sdata.getAccDatas().get(i);
                                line.append(",");
                                line.append(ad.getZ());
                            }
                            line.append(",");
                            line.append(fmt.format(new Date(sdata.getTimestamp())));
                            try {
                                this.csvWriter.write(line.toString() + "\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            String json = gson.toJson(sdata, StorageData.class);
                            DatagramPacket pkt = new DatagramPacket(json.getBytes(), json.getBytes().length, iAddr, this.udpUpPort);
                            socket.send(pkt);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            this.csvWriter.flush();
                        } catch (IOException e) {

                        }
                        c++;
                    }
                }
                socket.close();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    socket.close();
                }
                saveRunningFlag = false;
            }
        }
    }
}
