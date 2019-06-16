import java.util.ArrayList;

import twitter4j.Friendship;
import twitter4j.IDs;
import twitter4j.PagableResponseList;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.UserList;
import twitter4j.auth.AccessToken;

public class Account {
	protected final static int WHITE_LIST = 0;
	protected final static int BLACK_LIST = 1;

	protected String token;
	protected String tokenSecret;

	protected Twitter twitter;
	protected User user;

	protected String screenName;
	protected String name;
	protected long id;

	protected String profileImageURL;
	protected String biggerProfileImageURL;

	protected Long userListId = 0L;
	protected ResponseList<UserList> userLists;

	protected ArrayList<Long> whiteList = new ArrayList<Long>();
	protected ArrayList<Long> blackList = new ArrayList<Long>();

	public Account() {
	}

	public Account(String token, String tokenSecret) throws TwitterException {
		this.token = token;
		this.tokenSecret = tokenSecret;

		connect();
	}

	public void connect() throws TwitterException {
		AccessToken accessToken = new AccessToken(token, tokenSecret);
		twitter = new TwitterFactory().getInstance();
		twitter.setOAuthConsumer(SougoListMaker.CONSUMER_KEY, SougoListMaker.CONSUMER_SECRET);
		twitter.setOAuthAccessToken(accessToken);

		user = twitter.verifyCredentials();
		screenName = user.getScreenName();
		name = user.getName();
		id = user.getId();

		profileImageURL = user.getProfileImageURL();
		biggerProfileImageURL = user.getBiggerProfileImageURL();

		userLists = twitter.getUserLists(id);
	}

	void run() throws TwitterException {
		// 相互フォロー、リストメンバーを取得
		ArrayList<Long> listMembers = getListMembers();
		ArrayList<Long> mutualFollowers = getMutualFollowers();

		// リストを編集
		editList(listMembers, mutualFollowers);

		SougoListMaker.addText(this, "完了しました");
	}

	ArrayList<Long> getListMembers() throws TwitterException {
		SougoListMaker.addText(this, "リストメンバーを取得中");

		long cursor = -1L;
		PagableResponseList<User> users;
		ArrayList<Long> userIds = new ArrayList<Long>();

		do {
			users = twitter.getUserListMembers(userListId, cursor);

			for (User user : users) {
				userIds.add(user.getId());
			}

			cursor = users.getNextCursor();
		} while (users.hasNext());

		return userIds;
	}

	ArrayList<Long> getMutualFollowers() throws TwitterException {
		SougoListMaker.addText(this, "相互フォローのユーザを取得中");

		long cursor = -1L;
		IDs followersIds = null;
		ArrayList<Long> userIds = new ArrayList<Long>();
		userIds.addAll(whiteList);

		do {
			followersIds = twitter.getFollowersIDs(twitter.getId(), cursor, 100);
			ResponseList<Friendship> friendships = twitter.lookupFriendships(followersIds.getIDs());

			for (Friendship friendship : friendships) {
				if (friendship.isFollowing() && friendship.isFollowedBy()) {
					userIds.add(friendship.getId());
				}
			}

			cursor = followersIds.getNextCursor();
		} while (followersIds.hasNext());

		userIds.removeAll(blackList);

		return userIds;
	}

	void editList(ArrayList<Long> listMembers, ArrayList<Long> mutualFollowers)
			throws TwitterException {
		SougoListMaker.addText(this, "相互フォローをリストに追加中");

		for (Long mutualFollower : mutualFollowers) {
			if (!listMembers.contains(mutualFollower)) {
				twitter.createUserListMember(userListId, mutualFollower);
			}
		}

		SougoListMaker.addText(this, "相互フォローでないユーザをリストから削除中");

		for (Long listMember : listMembers) {
			if (!mutualFollowers.contains(listMember)) {
				twitter.destroyUserListMember(userListId, listMember);
			}
		}
	}

	public void setToken(String token) {
		this.token = token;
	}

	public void setTokenSecret(String tokenSecret) {
		this.tokenSecret = tokenSecret;
	}

	public void setWhiteList(ArrayList<Long> whiteList) {
		this.whiteList = whiteList;
	}

	public void setBlackList(ArrayList<Long> blackList) {
		this.blackList = blackList;
	}

	public void setUserListId(long userListId) {
		this.userListId = userListId;
	}

	public String getToken() {
		return token;
	}

	public String getTokenSecret() {
		return tokenSecret;
	}

	public ArrayList<Long> getWhiteList() {
		return whiteList;
	}

	public ArrayList<Long> getBlackList() {
		return blackList;
	}

	public long getUserListId() {
		return userListId;
	}
}
