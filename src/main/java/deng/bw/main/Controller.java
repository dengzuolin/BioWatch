package deng.bw.main;

import com.google.common.collect.EvictingQueue;
import com.sun.javafx.scene.control.TableColumnComparatorBase;
import de.felixroske.jfxsupport.FXMLController;
import deng.bw.model.MeasureData;
import deng.bw.model.WatchConfig;
import deng.bw.service.ComService;
import deng.bw.service.SerialState;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FXMLController
public class Controller implements Initializable {

    /***************** 腕表配置在表格中的行号 **********************/
    protected static int WB_CFG_TABLE_IDX_ID = 0;
    protected static int WB_CFG_TABLE_IDX_SAMPLE_ITV = 1;
    protected static int WB_CFG_TABLE_IDX_SAMPLE_LEN = 2;
    protected static int WB_CFG_TABLE_IDX_BATTERY = 3;
    protected static int WB_CFG_TABLE_IDX_DATA_MT = 4;
    protected static int WB_CFG_TABLE_IDX_DATA_FRQ = 5;
    protected static int WB_CFG_TABLE_IDX_TIME = 6;
    protected static int WB_CFG_TABLE_IDX_MODE = 7;

    @FXML
    ComboBox cbSerialList;
    @FXML
    ComboBox cbWatchMacList;
    @FXML
    TableView tvWatchConfig;
    @FXML
    Label lbBpm;
    @FXML
    Label lbOhm;
    @FXML
    Label lbAccX;
    @FXML
    Label lbAccY;
    @FXML
    Label lbAccZ;
    @FXML
    Label lbAccSqt;
    @FXML
    Button btnSerialConnection;
    @FXML
    Button btnWatchScan;
    @FXML
    Button btnWatchConnection;
    @FXML
    TableColumn tcConfigNames;
    @FXML
    TableColumn tcConfigValues;
    @FXML
    LineChart<String, Integer> lcPulseWave;
    @FXML
    LineChart<String, Integer> lcBpm;
    @FXML
    LineChart<String, Double> lcOhm;
    @FXML
    LineChart<String, Integer> lcAcc;

    LineChart<String, Float> lcBodyTemp;
    @FXML
    TableView tvMeasureData;
    @FXML
    TableColumn tcMeasureDataTime;
    @FXML
    TableColumn tcMeasureDataBpm;
    @FXML
    TableColumn tcMeasureDataOhm;
    @FXML
    TableColumn tcMeasureDataAcc;
    @FXML
    Button btnRecordSwitch;

    List<WatchConfig> watchConfigs = new ArrayList<>();

    Stage statusDialog;
    Label statusMsg;

    private ComService getComService() {
        ComService comService = BeanUtil.getBean(ComService.class);
        return comService;
    }

    private void initLineChart(LineChart chart) {
        if (chart == lcAcc) {
            ((XYChart.Series)chart.getData().get(0)).getData().clear();
            ((XYChart.Series)chart.getData().get(1)).getData().clear();
            ((XYChart.Series)chart.getData().get(2)).getData().clear();
        } else {
            ((XYChart.Series)chart.getData().get(0)).getData().clear();;
        }
        for (int i = 0; i < ComService.CHART_MAX; i++) {
            if (chart == lcBodyTemp) {
                ((XYChart.Series)chart.getData().get(0)).getData().add(new XYChart.Data<String, Float>(String.valueOf(i), 0.0f));
            } if (chart == lcAcc) {
                ((XYChart.Series)chart.getData().get(0)).getData().add(new XYChart.Data<String, Integer>(String.valueOf(i), 0));
                ((XYChart.Series)chart.getData().get(1)).getData().add(new XYChart.Data<String, Integer>(String.valueOf(i), 0));
                ((XYChart.Series)chart.getData().get(2)).getData().add(new XYChart.Data<String, Integer>(String.valueOf(i), 0));
            } else if (chart == lcOhm){
                ((XYChart.Series)chart.getData().get(0)).getData().add(new XYChart.Data<String, Double>(String.valueOf(i), 0.0d));
            } else if (chart == lcBpm) {
                ((XYChart.Series)chart.getData().get(0)).getData().add(new XYChart.Data<String, Integer>(String.valueOf(i), 0));
            } else if (chart == lcPulseWave) {
                ((XYChart.Series)chart.getData().get(0)).getData().add(new XYChart.Data<String, Integer>(String.valueOf(i), 0));
            }
        }
    }

    private void initLabels() {
        this.lbBpm.setText("");
        this.lbOhm.setText("");
        this.lbAccX.setText("");
        this.lbAccY.setText("");
        this.lbAccZ.setText("");
        this.lbAccSqt.setText("");
    }

    private void initCharts() {
        lcPulseWave.getData().add(new XYChart.Series<>());
        lcPulseWave.setCreateSymbols(false);
        lcPulseWave.getData().get(0).getNode().setStyle("-fx-stroke-width: 1px;");
        lcPulseWave.getXAxis().setTickLabelsVisible(false);
        lcPulseWave.getXAxis().setTickLength(1.0d);
        lcPulseWave.getXAxis().setLabel("");
        initLineChart(lcPulseWave);

        lcBpm.getData().add(new XYChart.Series<>());
        lcBpm.setCreateSymbols(false);
        lcBpm.getData().get(0).getNode().setStyle("-fx-stroke-width: 1px;");
        lcBpm.getXAxis().setTickLabelsVisible(false);
        lcBpm.getXAxis().setTickLength(1.0d);
        lcBpm.getXAxis().setLabel("");
        initLineChart(lcBpm);

        lcOhm.getData().add(new XYChart.Series<>());
        lcOhm.setCreateSymbols(false);
        lcOhm.setLegendVisible(false);
        lcOhm.getData().get(0).getNode().setStyle("-fx-stroke-width: 1px;");
        lcOhm.getXAxis().setTickLabelsVisible(false);
        lcOhm.getXAxis().setTickLength(1.0d);
        lcOhm.getXAxis().setLabel("");
        initLineChart(lcOhm);

        lcAcc.getData().add(new XYChart.Series<>());
        lcAcc.getData().add(new XYChart.Series<>());
        lcAcc.getData().add(new XYChart.Series<>());
        lcAcc.setCreateSymbols(false);
        lcAcc.setLegendVisible(false);
        lcAcc.getXAxis().setTickLabelsVisible(false);
        lcAcc.getXAxis().setTickLength(1.0d);
        lcAcc.getXAxis().setLabel("");
        initLineChart(lcAcc);

//        lcBodyTemp.getData().add(new XYChart.Series<>());
//        lcBodyTemp.setCreateSymbols(false);
//        lcBodyTemp.getData().get(0).getNode().setStyle("-fx-stroke-width: 1px;");
//        lcBodyTemp.getXAxis().setTickLabelsVisible(false);
//        lcBodyTemp.getXAxis().setTickLength(1.0d);
//        lcBodyTemp.getXAxis().setLabel("");
//        initLineChart(lcBodyTemp);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ComService comService = getComService();
        List<String> ports = comService.getSerialPorts();
        cbSerialList.getItems().clear();
        for (String p : ports)  {
            cbSerialList.getItems().add(p);
        }
        this.watchConfigs.add(new WatchConfig("ID",""));
        this.watchConfigs.add(new WatchConfig("采样间隔",""));
        this.watchConfigs.add(new WatchConfig("采样长度",""));
        this.watchConfigs.add(new WatchConfig("电池电量",""));
        this.watchConfigs.add(new WatchConfig("数据格式",""));
        this.watchConfigs.add(new WatchConfig("脉搏波降采帧率",""));
        this.watchConfigs.add(new WatchConfig("运动采集帧率",""));
        this.watchConfigs.add(new WatchConfig("皮电采样频率",""));
        this.watchConfigs.add(new WatchConfig("工作模式",""));

        tcConfigNames.setCellValueFactory(new PropertyValueFactory<>("name"));
        tcConfigValues.setCellValueFactory(new PropertyValueFactory<>("value"));
        tvWatchConfig.setItems(FXCollections.observableArrayList(this.watchConfigs));
        tvWatchConfig.refresh();

        initCharts();
        comService.setCharts(lcPulseWave, lcBpm, lcOhm, lcAcc);
        comService.setLabels(lbBpm, lbOhm, lbAccX, lbAccY, lbAccZ, lbAccSqt);
    }

    private void refreshSerialPorts() {
        ComService comService = getComService();
        String portSelected = (String)cbSerialList.getSelectionModel().getSelectedItem();
        List<String> ports = comService.getSerialPorts();
        cbSerialList.getItems().clear();
        for (String p : ports)  {
            cbSerialList.getItems().add(p);
        }
        if (portSelected != null) {
            cbSerialList.getSelectionModel().select(portSelected);
        }
    }

    public void switchSerialPort() {
        ComService comService = getComService();
        if (!comService.isSerialOpened()) {
            String portSelected = (String)cbSerialList.getSelectionModel().getSelectedItem();
            if (portSelected != null) {
                try {
                    comService.initSerialPort(portSelected);
                    btnSerialConnection.setText("断开串口");
                    btnWatchScan.setDisable(false);
                    btnWatchConnection.setDisable(false);
                    btnWatchConnection.setText("连接");
                } catch (Exception e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("错误");
                    alert.setHeaderText("串口打开失败[ERR:" + e.getMessage() + "]");
                    alert.showAndWait();
                }
            }
        } else {
            try {
                comService.closeSerialPort();
                btnSerialConnection.setText("打开串口");
                btnWatchScan.setDisable(true);
                btnWatchConnection.setDisable(true);
                btnWatchConnection.setText("连接");
                clearChart();
            } catch (Exception e) {
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("错误");
                alert.setHeaderText("串口断开失败[ERR:" + e.getMessage() + "]");
                alert.showAndWait();
            }
        }
    }

    private void showStatusDialog(String msg) {
        if (statusDialog == null) {
            statusDialog = new Stage();
            statusDialog.initStyle(StageStyle.UTILITY);
            statusMsg = new Label(); // 放一个标签
            AnchorPane anchorPane = new AnchorPane(statusMsg);
            Scene secondScene = new Scene(anchorPane, 310, 120);
            anchorPane.getChildren().get(0).setLayoutX(32);
            anchorPane.getChildren().get(0).setLayoutY(43);
            statusDialog.setScene(secondScene);
            statusDialog.setAlwaysOnTop(true);
        }
        statusMsg.setText(msg);
        statusDialog.show();
    }

    private void closeStatusDialog() {
        if (statusDialog.isShowing()) {
            statusDialog.close();
        }
    }

    public void scanWatch() {
        showStatusDialog("正在搜索设备，请稍候...");
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ComService comService = getComService();
        List<String> scanResult = comService.scanWatch();
        cbWatchMacList.getItems().clear();
        for (String d : scanResult) {
            cbWatchMacList.getItems().add(d);
        }
        if (scanResult.size() > 0) {
            cbWatchMacList.getSelectionModel().select(0);
        }
        closeStatusDialog();
    }

    public void switchWatchConnection() {
        ComService comService = getComService();
        if (comService.isWatchConnected()) {
            comService.disconnectWatch();
            btnWatchScan.setDisable(false);
            btnRecordSwitch.setDisable(true);
            btnWatchConnection.setText("连接");
            clearChart();
        } else {
            String w = (String)cbWatchMacList.getSelectionModel().getSelectedItem();
            if (w != null) {
                Pattern p = Pattern.compile("(.*):(.*)");
                Matcher m = p.matcher(w);
                if (m.find()) {
                    String mac = m.group(1);
                    showStatusDialog("正在连接设备，请稍候...");
                    if (comService.connectWatch(mac)) {
                        this.watchConfigs = comService.getWatchConfig();
                        tvWatchConfig.setItems(FXCollections.observableArrayList(this.watchConfigs));
                        tvWatchConfig.refresh();
                        btnWatchScan.setDisable(true);
                        btnRecordSwitch.setDisable(false);
                        btnWatchConnection.setText("断开");
                    };
                    closeStatusDialog();
                }
            }
        }
    }

    private void clearChart() {
        ((XYChart.Series)lcPulseWave.getData().get(0)).getData().clear();
        ((XYChart.Series)lcBpm.getData().get(0)).getData().clear();
        ((XYChart.Series)lcOhm.getData().get(0)).getData().clear();
        ((XYChart.Series)lcAcc.getData().get(0)).getData().clear();
        ((XYChart.Series)lcAcc.getData().get(1)).getData().clear();
        ((XYChart.Series)lcAcc.getData().get(2)).getData().clear();
//        ((XYChart.Series)lcBodyTemp.getData().get(0)).getData().clear();
    }

    public void switchRecordStatus() {
        ComService comService = getComService();
        if (comService.getSerialState().equals(SerialState.STANDBY)) {
            btnRecordSwitch.setText("结束采集");
            initCharts();
            initLabels();
            comService.startRecord();
        } else {
            btnRecordSwitch.setText("开始采集");
            comService.stopRecord();
        }
    }
}
