package com.bdindex.core;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.internal.Locatable;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.service.DriverService;
import com.bdindex.exception.IndexNeedBuyException;
import com.bdindex.exception.IndexNotInServiceException;
import com.bdindex.model.Model;
import com.bdindex.ui.MyTableModel;
import com.bdindex.ui.UIUpdateModel;
import com.bdindex.ui.Util;
import com.selenium.BDIndexAction;
import com.selenium.BDIndexBy;
import com.selenium.BDIndexJSExecutor;
import com.selenium.BDIndexUtil;
import com.selenium.Constant;
import com.selenium.ScreenShot;
import com.selenium.Wait;

public class BDIndexCoreWorker extends SwingWorker<Void, UIUpdateModel> {
	private static SimpleDateFormat logDateFormat = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");
	private static SimpleDateFormat yearMonthDateFormat = new SimpleDateFormat(
			"yyyyMM");
	private static SimpleDateFormat imgNameDateFormat = new SimpleDateFormat(
			"yyyy-MM-dd");
	private static Logger logger = Logger.getLogger(BDIndexCoreWorker.class);
	private DriverService service;
	private WebDriver webdriver;
	private MyTableModel tableModel;
	private ArrayList<AbstractButton> buttons;
	private JTextArea textArea;

	@SuppressWarnings("unused")
	private BDIndexCoreWorker() {
	}

	public BDIndexCoreWorker(MyTableModel myTableModel,
			ArrayList<AbstractButton> buttons, JTextArea textArea) {
		tableModel = myTableModel;
		this.buttons = buttons;
		this.textArea = textArea;
	}

	private void init() throws Exception {
		service = new ChromeDriverService.Builder()
				.usingDriverExecutable(BDIndexUtil.getDriverFileFromJar())
				.usingAnyFreePort().build();
		service.start();
		webdriver = new RemoteWebDriver(service.getUrl(),
				new ChromeOptions());
		// 第一次发送请求
		webdriver.get(Constant.url);
		// 处理错误页面
		BDIndexUtil.handleErrorPageBeforeLogin(webdriver, 3);
		// 激活浏览器窗口,将浏览器窗口置顶,并不是真正要截图
		((TakesScreenshot) webdriver).getScreenshotAs(OutputType.BYTES);
		// 最大化窗口
		BDIndexAction.maximizeBrowser(webdriver);
		// 处理错误页面
		BDIndexUtil.handleErrorPageBeforeLogin(webdriver, 3);
		// 登录
		BDIndexAction.login(webdriver, service, 3);
		BDIndexUtil.handleErrorPage(webdriver);
	}

	/**
	 * 推算百度指数
	 * 
	 * @param keyword
	 * @param startDate
	 * @param endDate
	 * @throws Exception
	 */
	private void estimateBDIndex(String keyword, Date startDate, Date endDate)
			throws Exception {
		ArrayList<Date[]> list = Util.getDatePairsBetweenDates(startDate,
				endDate);
		String outputDir = BDIndexUtil
				.getOutputDir(keyword, startDate, endDate);
		String outputBDIndexFilePath = BDIndexUtil.getBDIndexDataFilePath(
				keyword, startDate, endDate);
		for (int i = 0; i < list.size(); i++) {
			estimatedAction(keyword, list.get(i)[0], list.get(i)[1], outputDir,
					outputBDIndexFilePath);
		}
	}

	/**
	 * 推算百度指数核心操作
	 * 
	 * @param keyword
	 * @param startDate
	 * @param endDate
	 * @throws Exception
	 */
	private void estimatedAction(String keyword, Date startDate, Date endDate,
			String outputDir, String outputBDIndexFilePath) throws Exception {
		// 这很重要
		BDIndexUtil.setStartDate(startDate);
		BDIndexUtil.setEndDate(endDate);
		BDIndexUtil.setCurrentKeyword(keyword);
		submitKeyword(keyword);
		// 找到trend/svg/rect区域
		WebElement rectElement = null;
		BDIndexAction.retryCustomizeDate(webdriver, startDate, endDate, 3);
		BDIndexUtil.retryWaitRectElement(webdriver, service, 5, 4);

		rectElement = webdriver.findElement(BDIndexBy.bdIndexRect);
		// 使操作区域进入视野范围
		Point pointInViewport = ((Locatable) rectElement).getCoordinates()
				.inViewPort();
		// 将鼠标移动到截图内容区域以外
		int mouseY = BDIndexUtil.getMouseY(webdriver, rectElement,
				pointInViewport);
		// 鼠标移动事件
		Robot robot = new Robot();
		// 鼠标点击用以激活鼠标所在的浏览器窗口
		robot.mouseMove(pointInViewport.x - 20, mouseY);
		robot.mousePress(InputEvent.BUTTON1_MASK);
		robot.mouseRelease(InputEvent.BUTTON1_MASK);
		// 截图
		String trendFilename = getTrendFileName(keyword, startDate, endDate);
		ScreenShot.captureTrendPicForEstimatedMode((TakesScreenshot) webdriver,
				rectElement, trendFilename, outputDir);
		long days = BDIndexUtil.getDaysFromURL(webdriver.getCurrentUrl());
		// 截取纵向刻度值
		String yAxisFilename = getYAxisFileName(startDate, endDate);
		BDIndexEstimateUtil.extractYAxisValue(webdriver, service,
				yAxisFilename, outputDir);
		// 计算估算值
		int bdindexs[] = BDIndexEstimateUtil.doEstimatedValue(outputDir
				+ yAxisFilename, outputDir + trendFilename, days);
		BDIndexEstimateUtil.writeEstimateBDIndexToFile(startDate, bdindexs,
				outputBDIndexFilePath);
	}

	/**
	 * 输入关键词进行搜索
	 * 
	 * @param keyword
	 * @param startDate
	 * @param endDate
	 * @throws Exception
	 */
	private void submitKeyword(String keyword)
			throws Exception {
		// 处理错误页
		BDIndexUtil.handleErrorPage(webdriver);
		// 输入关键字搜索
		BDIndexAction.searchKeyword(webdriver, keyword);
		// 处理关键词需购买的情况
		BDIndexUtil.checkBuyIndexPage(webdriver, service, keyword);
		// 处理关键词不提供服务的情况
		BDIndexUtil.indexNotInServiceCheck(webdriver, service, keyword);
	}

	/**
	 * 精确抓取百度指数
	 */
	private void accurateBDIndex(String keyword, Date startDate, Date endDate)
			throws Exception {
		ArrayList<Date[]> list = Util.getDatePairsBetweenDates(startDate,
				endDate);
		String outputDir = BDIndexUtil
				.getOutputDir(keyword, startDate, endDate);
		submitKeyword(keyword);
		String url = webdriver.getCurrentUrl();
		if (url.contains("time=")) {
			return;
		}
		String res = (String)((JavascriptExecutor) webdriver).executeScript("return PPval.ppt;");
		String res2 = (String)((JavascriptExecutor) webdriver).executeScript("return PPval.res2;");
		for (int i = 0; i < list.size(); i++) {
			//此处为快速抓取百度指数代码
			//list.get(i)[0]--startDate
			//list.get(i)[1]--endDate
			Date subStartDate = list.get(i)[0];
			Date subEndDate = list.get(i)[1];
			String []wiseIndices = BDIndexJSExecutor.requestWiseIndex(webdriver,keyword,res, res2, subStartDate, subEndDate);
			//每次des和image都是不同的，要对应起来
			Calendar tmpCalendar = Calendar.getInstance();
			for (int j = 0; j < wiseIndices.length; j++) {
				String desc = BDIndexJSExecutor.requestImageDes(webdriver,res, res2, wiseIndices[j]);
				String html =  "\"<table style='background-color: #444;'><tbody><tr><td class='view-value'>";
				html += desc.replaceAll("\"", "'");
				html += "</td></tr></tbody></table>\"";
				
				//将渲染后的百度指数div添加到百度指数页面
				int retryCount = 0;
				By indexBy = By.xpath("/html/body/div/table/tbody/tr/td");
				while (retryCount < 5) {
					((JavascriptExecutor)webdriver).executeScript( 
							"var body = document.getElementsByTagName('body');" + 
							"var newDiv = document.createElement('div');" + 
							"newDiv.setAttribute('name', 'songgeb');"+
							"newDiv.innerHTML = " + html  +";" +
							"body[0].appendChild(newDiv);");
					try {
						Wait.waitForElementVisible(webdriver, indexBy, 10);
						webdriver.findElement(indexBy);
						break;
					} catch(Exception e) {
						retryCount ++;
						((JavascriptExecutor)webdriver).executeScript(""
								+ "var e = document.getElementsByName('songgeb');\n" + 
								"e[0].parentNode.removeChild(e[0]);");
					}
				}
				
				//截图
				WebElement targetEle = webdriver.findElement(indexBy);
				tmpCalendar.clear();
				tmpCalendar.setTime(subStartDate);
				tmpCalendar.add(Calendar.DAY_OF_MONTH, j);
				String imgFileName = imgNameDateFormat.format(tmpCalendar.getTime());
				ScreenShot.capturePicForAccurateMode(
						(TakesScreenshot) webdriver, targetEle,
						imgFileName + ".png", outputDir);
				//删除添加的百度指数div
				((JavascriptExecutor)webdriver).executeScript(""
						+ "var e = document.getElementsByName('songgeb');\n" + 
						"e[0].parentNode.removeChild(e[0]);");
			}
			
			//以下为精确抓取代码
//			accurateAction(keyword, list.get(i)[0], list.get(i)[1], outputDir);
		}
		OCRUtil.doOCR(outputDir, outputDir);
	}

	/**
	 * 精确抓取核心操作
	 * 
	 * @param keyword
	 * @param startDate
	 * @param endDate
	 */
	@SuppressWarnings("unused")
	private void accurateAction(String keyword, Date startDate, Date endDate,
			String outputDir) throws Exception {
		// 这很重要
		BDIndexUtil.setStartDate(startDate);
		BDIndexUtil.setEndDate(endDate);
		BDIndexUtil.setCurrentKeyword(keyword);
		submitKeyword(keyword);
		// 找到trend/svg/rect区域
		WebElement rectElement = null;
		BDIndexAction.retryCustomizeDate(webdriver, startDate, endDate, 3);
		BDIndexUtil.retryWaitRectElement(webdriver, service, 5, 4);
		rectElement = webdriver.findElement(BDIndexBy.bdIndexRect);
		// 使操作区域进入视野范围
		Point pointInViewport = ((Locatable) rectElement).getCoordinates()
				.inViewPort();
		// 计算鼠标应设置的Y值
		int mouseY = BDIndexUtil.getMouseY(webdriver, rectElement,
				pointInViewport);
		// 计算鼠标X轴单步移动距离(读取当前url的时间参数,计算步长,默认30天)
		long days = BDIndexUtil.differentDays(startDate, endDate);
		int rectWidth = rectElement.getSize().width;
		float step = (rectWidth * 1.0f) / (days - 1);
		// 鼠标移动事件
		Robot robot = new Robot();
		// 一边移动鼠标一边截图
		// 鼠标点击用以激活鼠标所在的浏览器窗口
		robot.mouseMove(pointInViewport.x - 20, mouseY);
		robot.mousePress(InputEvent.BUTTON1_MASK);
		robot.mouseRelease(InputEvent.BUTTON1_MASK);
		Thread.sleep(500);
		
		// 开始移动
		for (int i = 0; i < days; i++) {
			int mouseX = BDIndexUtil.getMouseX(rectWidth, pointInViewport,
					days, step, i);
			robot.mouseMove(mouseX, mouseY);
			Thread.sleep(500);
			try {
				// 第一层过滤:1. 特殊节点如重要事件 2. 页面自动刷新 3. 黑框不出现
				BDIndexUtil.retryWaitBlackBoxDisplay(webdriver, service, robot,
						mouseX, mouseY, step, i, 5, 4);
				// 保留当前时间
				BDIndexUtil.setCurrentDateString(BDIndexUtil
						.getCurrentDateString(webdriver, service, robot,
								mouseX, mouseY, step, i, 4, 4));
				// 第二层过滤: 1. 页面自动刷新 2. 数字不显示(不能保证百分之百)
				BDIndexUtil.retryWaitIndexNumElement(webdriver, service, robot,
						mouseX, mouseY, step, i, 5, 8);
				// 截图工作
				BDIndexUtil.retryScreenShot(webdriver, service, keyword, robot,
						mouseX, mouseY, step, i, 4, 4, outputDir);
			} catch (Exception e) {
				logger.error(keyword + " : 发生异常,跳过当前节点", e);
				continue;
			}
		}
	}

	private void start() {
		// 防御式编程
		ArrayList<Model> models = tableModel.getValues();
		if (models.size() < 1) {
			logger.warn("关键词数据源0条");
			return;
		}
		// 开始
		publish(new UIUpdateModel(getTextAreaContent(null,
				Constant.Status.Spider_Start), false));
		// 初始化
		try {
			init();
		} catch (Exception e) {
			publish(new UIUpdateModel(getTextAreaContent(null,
					Constant.Status.Spider_InitException), true));
			logger.error(Constant.Status.Spider_InitException, e);
			BDIndexUtil.closeSession(webdriver, service);
			return;
		}
		// 执行过程
		UIUpdateModel updateModel = null;
		long startTime = 0;
		for (Model model : models) {
			startTime = System.currentTimeMillis();
			model.setStatus(Constant.Status.Model_Start);
			publish(new UIUpdateModel(getTextAreaContent(model.getKeyword(),
					Constant.Status.Model_Start), false));
			try {
				switch (Constant.currentMode) {
				case Estimate:
					estimateBDIndex(model.getKeyword(), model.getStartDate(),
							model.getEndDate());
					break;
				case Accurate:
					accurateBDIndex(model.getKeyword(), model.getStartDate(),
							model.getEndDate());
					break;
				default:
					return;
				}
				model.setStatus(Constant.Status.Model_End);
				updateModel = new UIUpdateModel(getTextAreaContent(
						model.getKeyword(), Constant.Status.Model_End), false);
			} catch (IndexNeedBuyException e) {
				model.setStatus(Constant.Status.Model_IndexNeedBuyException);
				updateModel = new UIUpdateModel(getTextAreaContent(
						model.getKeyword(),
						Constant.Status.Model_IndexNeedBuyException), false);
			} catch (IndexNotInServiceException e) {
				model.setStatus(Constant.Status.Model_IndexNotInServiceException);
				updateModel = new UIUpdateModel(getTextAreaContent(
						model.getKeyword(),
						Constant.Status.Model_IndexNotInServiceException),
						false);
			} catch (Exception e) {
				model.setStatus(Constant.Status.Model_Exception);
				updateModel = new UIUpdateModel(getTextAreaContent(
						model.getKeyword(), Constant.Status.Model_Exception),
						false);
				logger.error(model.getKeyword(), e);
				// 删除当前关键词的结果文件,防止不必要麻烦
				BDIndexUtil.deleteIndexFile(model);
			} finally {
				model.setTime((System.currentTimeMillis() - startTime) / 1000);
				publish(updateModel);
				// 数据汇总统计
				BDIndexSummaryUtil.summary(model);
			}
			// 记录爬虫信息
			String spiderInfoFilePath = BDIndexUtil.getOutputDir(
					model.getKeyword(), model.getStartDate(),
					model.getEndDate())
					+ Constant.spiderinfoFilename;
			Util.writeSpiderInfoToFile(spiderInfoFilePath, model);
		}
		publish(new UIUpdateModel(getTextAreaContent(null,
				Constant.Status.Spider_End), true));
		BDIndexUtil.closeSession(webdriver, service);
	}

	/**
	 * 日志日期
	 * 
	 * @return
	 */
	private String logDateString() {
		return "【" + logDateFormat.format(new Date()) + "】";
	}

	/**
	 * 刻度图片文件名
	 * 
	 * @param startDate
	 * @param endDate
	 * @return
	 */
	private String getYAxisFileName(Date startDate, Date endDate) {
		return yearMonthDateFormat.format(startDate) + "-"
				+ yearMonthDateFormat.format(endDate) + ".png";
	}

	/**
	 * 曲线图文件名
	 * 
	 * @param keyword
	 * @param startDate
	 * @param endDate
	 * @return
	 */
	private String getTrendFileName(String keyword, Date startDate, Date endDate) {
		return keyword + "(" + yearMonthDateFormat.format(startDate) + "-"
				+ yearMonthDateFormat.format(endDate) + ")" + ".png";
	}

	/**
	 * 用于在textArea显示的内容
	 * 
	 * @param keyword
	 * @param status
	 * @return
	 */
	private String getTextAreaContent(String keyword, String status) {
		if (keyword == null || keyword.equals("")) {
			return status + logDateString() + "\n";
		}
		return "【" + keyword + "】" + status + logDateString() + "\n";
	}

	@Override
	protected Void doInBackground() throws Exception {
		start();
		return null;
	}

	@Override
	protected void process(List<UIUpdateModel> chunks) {
		super.process(chunks);
		for (int i = 0; i < chunks.size(); i++) {
			UIUpdateModel model = chunks.get(i);
			textArea.append(model.getTextAreaContent());
			Util.setButtonsStatus(buttons, model.isButtonEnable());
			tableModel.fireTableDataChanged();
		}
	}
}