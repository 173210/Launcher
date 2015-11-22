package application.scene.menu;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.StringJoiner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import utility.DataUtil;
import utility.ErrorUtil;
import utility.PlatformUtil;
import utility.StringUtil;
import utility.XMLUtil;

public class MenuController implements Initializable {

	/** 作品名 */
	@FXML
	private Text workName;

	/** 製作者名 */
	@FXML
	private Text creatorName;

	/** 作品の説明 */
	@FXML
	private Text descriptionText;

	/** タイトル */
	@FXML
	private Text title;

	/** 開くボタン */
	@FXML
	private Button defButton;

	/** 画像を読み込むビュー */
	@FXML
	private ImageView imageView;

	/** 作品リスト */
	@FXML
	private ListView<String> listView;

	private Element root;

	private HashMap<Integer, HashMap<String, String>> dataMap;

	private int pivot = 0;

	private int size = 0;

	private ObservableList<String> listRecords = FXCollections.observableArrayList();

	private Timeline timeline;

	@FXML
	public void handleUp(ActionEvent event) {
		changeOverFile();
	}

	/**
	 * 一つ上のファイルに移動する
	 */
	private void changeOverFile() {
		pivot--;
		if (pivot < 0)
			pivot = size - 1;

		listView.getSelectionModel().select(pivot);
		setField();
	}

	@FXML
	public void handleDown(ActionEvent event) {
		changeUnderFile();
	}
	/**
	 * 一つ下のファイルに移動する
	 */
	private void changeUnderFile() {
		pivot++;
		if (pivot >= size)
			pivot = 0;

		listView.getSelectionModel().select(pivot);
		setField();
	}

	@FXML
	public void handleEnter(ActionEvent event) {
		openDirectory();
	}

	/**
	 * 現在該当するディレクトリを開く
	 */
	private void openDirectory() {
		//現在のディレクトリのパス
		String currentDirectory = DataUtil.getCurrentDirectory();
		//works直下のいずれかの作品名
		String fileName = dataMap.get(pivot + 1).get("title");
		//選択した作品のパス
		String path = new StringJoiner(PlatformUtil.getSeparator()).add(currentDirectory)
				.add(StringUtil.WORK_DIRECTORY_NAME).add(fileName)
				.toString();
		//pathをエスケープするべき
		String command = "explorer";
		if (PlatformUtil.isMac()) {
			command = "open";
		}
		ProcessBuilder processBuilder = new ProcessBuilder(command, path);
		try {
			Process process = processBuilder.start();
			process.waitFor();
			process.destroy();
		} catch (IOException | InterruptedException e) {
			ErrorUtil.getInstance().printLog(e);
		}

	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		root = XMLUtil.getInstance().getRoot();

		dataMap = new HashMap<Integer, HashMap<String, String>>();

		//ルート要素の子ノードを取得する
		NodeList rootChildren = root.getChildNodes();
		for (int i = 0; i < rootChildren.getLength(); i++) {
			//i個目の作品情報を取得
			Node node = rootChildren.item(i);
			HashMap<String, String> map = new HashMap<>();
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) node;
				if (element.getNodeName().equals("work")) {
					NodeList personChildren = node.getChildNodes();
					for (int j = 0; j < personChildren.getLength(); j++) {
						Node personNode = personChildren.item(j);
						if (personNode.getNodeType() == Node.ELEMENT_NODE) {
							setData(personNode, map, "title", "creator", "description", "path");
						}
					}
				}
			}
			if (!map.isEmpty()) {
				String workPath = map.get("path");
				String[] split = map.get("image").split(",");
				StringJoiner joiner = new StringJoiner(",");
				for (String fileName : split) {
					joiner.add(new StringJoiner("/").add(StringUtil.WORK_DIRECTORY_NAME).add(workPath)
							.add(fileName).toString());
				}
				map.put("image", joiner.toString());
				dataMap.put((i / 2) + 1, map);
			}
		}

		size = dataMap.size();
		pivot = 0;
		setField();
		initKeyConfig();
		initListView();

		//画面サイズの設定
		imageView.setPreserveRatio(true);
		imageView.setFitHeight(StringUtil.IMAGE_HEIGHT);
		imageView.setFitWidth(StringUtil.IMAGE_WIDTH);

		title.setText(StringUtil.LAUNCHER_NAME);
		if (PlatformUtil.isMac()) {
			descriptionText.setFont(Font.font("YuGothic"));
		} else if (PlatformUtil.isWindows()) {
			descriptionText.setFont(Font.font("Meiryo"));
		}
	}

	/**
	 * ListViewの初期化処理
	 */
	private void initListView() {
		listView.setItems(listRecords);
		listView.getSelectionModel().selectFirst();
		listView.setOnMouseClicked(event -> {
			if (event.getClickCount() == 2) {
				openDirectory();
			} else {
				pivot = listView.getSelectionModel().getSelectedIndex();
				setField();
			}
		});
	}

	/**
	 * キー情報の初期化
	 */
	private void initKeyConfig() {
		listView.setOnKeyPressed(event -> {
			KeyCode code = event.getCode();
			if (KeyCode.ENTER.equals(code)) {
				openDirectory();
			} else if (KeyCode.DOWN.equals(code)) {
				changeUnderFile();
			} else if (KeyCode.UP.equals(code)) {
				changeOverFile();
			}
		});
	}

	/**
	 * Fieldの初期化
	 */
	private void setField() {
		HashMap<String, String> map = dataMap.get(pivot + 1);
		if (map != null) {
			workName.setText("作品名:" + map.get("title"));
			creatorName.setText("製作者:" + map.get("creator"));
			String descriptionValue = map.get("description");
			formatDescriptionText(descriptionValue);

			//画像のパスを分割
			String[] split = map.get("image").split(",");
			startImageAnimation(split);
		} else {
			ErrorUtil.getInstance().printLog(new FileNotFoundException());
			//			再現方法:1にカーソルがあるが、実際は6が表示されているときに6を開き、その後下に行こうとすると発生する
			//			対策として最初から最後に移動できないようにした
		}
	}

	/**
	 * 引数の画像名から画像を取得し、アニメーションを開始する
	 * @param split - 画像名の配列
	 */
	private void startImageAnimation(String[] split) {
		//imageView.setImage(new Image(split[split.length - 1]));
		imageView.setImage(new Image(split[split.length - 1], StringUtil.SCENE_WIDTH,
				StringUtil.SCENE_HEIGHT, false,
				true));
		//すでにアニメーションが行われていた場合削除する
		if (timeline != null) {
			timeline.stop();
		}
		int count = 0;
		timeline = new Timeline();
		for (String str : split) {
			timeline.getKeyFrames().add(new KeyFrame(new Duration((++count) * 1000), event -> {
				imageView.setImage(
						new Image(str, StringUtil.SCENE_WIDTH, StringUtil.SCENE_HEIGHT, false,
								true));
			}));
		}
		//アニメーションの無限ループ
		timeline.setCycleCount(Timeline.INDEFINITE);
		timeline.play();
	}

	/**
	 * ゲーム説明のテキストを適切な長さにフォーマットする
	 * @param descriptionValue - 変換前のテキスト
	 */
	private void formatDescriptionText(String descriptionValue) {
		//		int limit = 30;//1行に何文字まで表示するか
		//		if (descriptionValue.length() < limit) {
		//			descriptionText.setText(descriptionValue);
		//		} else {
		//			String crlf = System.getProperty("line.separator");
		//			StringJoiner joiner = new StringJoiner(crlf);
		//			for (int i = 0; limit * i + limit < descriptionValue.length(); i++) {
		//				joiner.add(descriptionValue.substring(limit * i, limit * i + limit));
		//			}
		//			descriptionText.setText(joiner.toString());
		//		}
		descriptionText.setText(descriptionValue);
	}

	/**
	 * 対応する情報をHashMapに格納する
	 * @param personNode - 処理対処のノード
	 */
	private void setData(Node personNode, HashMap<String, String> map, String... args) {
		for (String name : args) {
			if (personNode.getNodeName().equals(name)) {
				map.put(name, personNode.getTextContent());
				if (personNode.getNodeName().equals("title")) {
					//Listにタイトルをセット
					listRecords.add(personNode.getTextContent());
					//現在のディレクトリのパス
					String currentDirectory = DataUtil.getCurrentDirectory();

					//選択した作品のパス
					String path = new StringJoiner("/").add(currentDirectory).add(StringUtil.WORK_DIRECTORY_NAME)
							.add(personNode.getTextContent()).add(StringUtil.SCREENSHOT_PATH).toString();

					File[] list = new File(path).listFiles();
					StringJoiner joiner = new StringJoiner(",");
					if (!Objects.isNull(list)) {
						for (File fileName : list) {
							joiner.add(fileName.getName());
						}
						map.put("image", joiner.toString());
					} else {
						ErrorUtil.getInstance().printLog(new FileNotFoundException());
					}
					//TODO:名前は外部,画像は内部(内部に統一したほうがいい)
				}
			}
		}
	}
}
