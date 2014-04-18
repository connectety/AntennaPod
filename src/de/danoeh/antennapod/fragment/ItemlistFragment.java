package de.danoeh.antennapod.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.ItemviewActivity;
import de.danoeh.antennapod.adapter.DefaultActionButtonCallback;
import de.danoeh.antennapod.adapter.InternalFeedItemlistAdapter;
import de.danoeh.antennapod.asynctask.DownloadObserver;
import de.danoeh.antennapod.asynctask.ImageLoader;
import de.danoeh.antennapod.dialog.FeedItemDialog;
import de.danoeh.antennapod.feed.EventDistributor;
import de.danoeh.antennapod.feed.Feed;
import de.danoeh.antennapod.feed.FeedItem;
import de.danoeh.antennapod.feed.FeedMedia;
import de.danoeh.antennapod.service.download.DownloadService;
import de.danoeh.antennapod.service.download.Downloader;
import de.danoeh.antennapod.storage.DBReader;
import de.danoeh.antennapod.storage.DownloadRequester;
import de.danoeh.antennapod.util.QueueAccess;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Displays a list of FeedItems.
 */
@SuppressLint("ValidFragment")
public class ItemlistFragment extends ListFragment {
    private static final String TAG = "ItemlistFragment";

    private static final int EVENTS = EventDistributor.DOWNLOAD_HANDLED
            | EventDistributor.DOWNLOAD_QUEUED
            | EventDistributor.QUEUE_UPDATE
            | EventDistributor.UNREAD_ITEMS_UPDATE;

    public static final String EXTRA_SELECTED_FEEDITEM = "extra.de.danoeh.antennapod.activity.selected_feeditem";
    public static final String ARGUMENT_FEED_ID = "argument.de.danoeh.antennapod.feed_id";

    protected InternalFeedItemlistAdapter adapter;

    private long feedID;
    private Feed feed;
    protected QueueAccess queue;

    private boolean itemsLoaded = false;
    private boolean viewsCreated = false;

    private AtomicReference<Activity> activity = new AtomicReference<Activity>();

    private DownloadObserver downloadObserver;
    private List<Downloader> downloaderList;

    private FeedItemDialog feedItemDialog;


    /**
     * Creates new ItemlistFragment which shows the Feeditems of a specific
     * feed. Sets 'showFeedtitle' to false
     *
     * @param feedId The id of the feed to show
     * @return the newly created instance of an ItemlistFragment
     */
    public static ItemlistFragment newInstance(long feedId) {
        ItemlistFragment i = new ItemlistFragment();
        Bundle b = new Bundle();
        b.putLong(ARGUMENT_FEED_ID, feedId);
        i.setArguments(b);
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        Bundle args = getArguments();
        if (args == null) throw new IllegalArgumentException("args invalid");
        feedID = args.getLong(ARGUMENT_FEED_ID);

        startItemLoader();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventDistributor.getInstance().register(contentUpdate);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventDistributor.getInstance().unregister(contentUpdate);
        stopItemLoader();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateProgressBarVisibility();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        stopItemLoader();
        adapter = null;
        viewsCreated = false;
        activity.set(null);
        if (downloadObserver != null) {
            downloadObserver.onPause();
        }
        feedItemDialog = null;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity.set(activity);
        if (downloadObserver != null) {
            downloadObserver.setActivity(activity);
            downloadObserver.onResume();
        }
        if (viewsCreated && itemsLoaded) {
            onFragmentLoaded();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewsCreated = true;
        if (itemsLoaded) {
            onFragmentLoaded();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        FeedItem selection = adapter.getItem(position - l.getHeaderViewsCount());
        /*
        Intent showItem = new Intent(getActivity(), ItemviewActivity.class);
        showItem.putExtra(FeedlistFragment.EXTRA_SELECTED_FEED, selection
                .getFeed().getId());
        showItem.putExtra(EXTRA_SELECTED_FEEDITEM, selection.getId());

        startActivity(showItem);
        */
        feedItemDialog = new FeedItemDialog(activity.get(), selection, queue);
        feedItemDialog.show();
    }

    private EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {

        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((EVENTS & arg) != 0) {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Received contentUpdate Intent.");
                if ((EventDistributor.DOWNLOAD_QUEUED & arg) != 0) {
                    updateProgressBarVisibility();
                } else {
                    startItemLoader();
                    updateProgressBarVisibility();
                }
            }
        }
    };

    private void updateProgressBarVisibility() {
        if (feed != null) {
            if (DownloadService.isRunning
                    && DownloadRequester.getInstance().isDownloadingFile(feed)) {
                ((ActionBarActivity) getActivity())
                        .setSupportProgressBarIndeterminateVisibility(true);
            } else {
                ((ActionBarActivity) getActivity())
                        .setSupportProgressBarIndeterminateVisibility(false);
            }
            getActivity().supportInvalidateOptionsMenu();
        }
    }

    private void onFragmentLoaded() {
        if (adapter == null) {
            getListView().setAdapter(null);
            setupHeaderView();
            adapter = new InternalFeedItemlistAdapter(getActivity(), itemAccess, new DefaultActionButtonCallback(activity.get()), false);
            setListAdapter(adapter);
            downloadObserver = new DownloadObserver(activity.get(), new Handler(), downloadObserverCallback);
            downloadObserver.onResume();
        }
        setListShown(true);
        adapter.notifyDataSetChanged();

        if (feedItemDialog != null && feedItemDialog.isShowing()) {
            feedItemDialog.setItemFromCollection(feed.getItems());
            feedItemDialog.setQueue(queue);
            feedItemDialog.updateMenuAppearance();
        }


    }

    private DownloadObserver.Callback downloadObserverCallback = new DownloadObserver.Callback() {
        @Override
        public void onContentChanged() {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            if (feedItemDialog != null && feedItemDialog.isShowing()) {
                feedItemDialog.updateMenuAppearance();
            }
        }

        @Override
        public void onDownloadDataAvailable(List<Downloader> downloaderList) {
            ItemlistFragment.this.downloaderList = downloaderList;
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    };

    private void setupHeaderView() {
        if (getListView() == null || feed == null) {
            Log.e(TAG, "Unable to setup listview: listView = null or feed = null");
            return;
        }
        LayoutInflater inflater = (LayoutInflater)
                activity.get().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View header = inflater.inflate(R.layout.feeditemlist_header, null);
        getListView().addHeaderView(header);

        TextView txtvTitle = (TextView) header.findViewById(R.id.txtvTitle);
        TextView txtvAuthor = (TextView) header.findViewById(R.id.txtvAuthor);
        TextView txtvLink = (TextView) header.findViewById(R.id.txtvLink);
        ImageView imgvCover = (ImageView) header.findViewById(R.id.imgvCover);

        txtvTitle.setText(feed.getTitle());
        txtvAuthor.setText(feed.getAuthor());
        txtvLink.setText(feed.getLink());
        Linkify.addLinks(txtvLink, Linkify.WEB_URLS);
        ImageLoader.getInstance().loadThumbnailBitmap(feed.getImage(), imgvCover,
                (int) getResources().getDimension(R.dimen.thumbnail_length_onlinefeedview));
    }

    private InternalFeedItemlistAdapter.ItemAccess itemAccess = new InternalFeedItemlistAdapter.ItemAccess() {

        @Override
        public FeedItem getItem(int position) {
            return (feed != null) ? feed.getItemAtIndex(true, position) : null;
        }

        @Override
        public int getCount() {
            return (feed != null) ? feed.getNumOfItems(true) : 0;
        }

        @Override
        public boolean isInQueue(FeedItem item) {
            return (queue != null) && queue.contains(item.getId());
        }

        @Override
        public int getItemDownloadProgressPercent(FeedItem item) {
            if (downloaderList != null) {
                for (Downloader downloader : downloaderList) {
                    if (downloader.getDownloadRequest().getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                            && downloader.getDownloadRequest().getFeedfileId() == item.getMedia().getId()) {
                        return downloader.getDownloadRequest().getProgressPercent();
                    }
                }
            }
            return 0;
        }
    };

    private ItemLoader itemLoader;

    private void startItemLoader() {
        if (itemLoader != null) {
            itemLoader.cancel(true);
        }
        itemLoader = new ItemLoader();
        itemLoader.execute(feedID);
    }

    private void stopItemLoader() {
        if (itemLoader != null) {
            itemLoader.cancel(true);
        }
    }

    private class ItemLoader extends AsyncTask<Long, Void, Object[]> {
        @Override
        protected Object[] doInBackground(Long... params) {
            long feedID = params[0];
            Context context = activity.get();
            if (context != null) {
                return new Object[]{DBReader.getFeed(context, feedID),
                        QueueAccess.IDListAccess(DBReader.getQueueIDList(context))};
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Object[] res) {
            super.onPostExecute(res);
            if (res != null) {
                feed = (Feed) res[0];
                queue = (QueueAccess) res[1];
                itemsLoaded = true;
                if (viewsCreated) {
                    onFragmentLoaded();
                }
            }
        }
    }
}
