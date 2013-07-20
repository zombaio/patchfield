package com.noisepages.nettoyeur.patchbay.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.Notification;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.ToggleButton;

import com.noisepages.nettoyeur.patchbay.IPatchbayService;

public final class PatchView extends FrameLayout {

  private IPatchbayService patchbay;
  private final List<String> modules = new ArrayList<String>();
  private final Map<Pair<String, Integer>, List<Pair<String, Integer>>> connections =
      new HashMap<Pair<String, Integer>, List<Pair<String, Integer>>>();

  public PatchView(Context context) {
    super(context);
  }

  public PatchView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public PatchView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public void setPatchbay(IPatchbayService patchbay) {
    this.patchbay = patchbay;
  }

  public void addModule(String module, int inputChannels, int outputChannels,
      Notification notification) {
    for (String u : modules) {
      int sinks = 0;
      try {
        sinks = patchbay.getInputChannels(u);
      } catch (RemoteException e) {
        e.printStackTrace();
        continue;
      }
      for (int i = 0; i < outputChannels; ++i) {
        for (int j = 0; j < sinks; ++j) {
          try {
            if (patchbay.isConnected(module, i, u, j)) {
              addConnection(module, i, u, j);
            }
          } catch (RemoteException e) {
            e.printStackTrace();
          }
        }
      }
      int sources = 0;
      try {
        sources = patchbay.getOutputChannels(u);
      } catch (RemoteException e) {
        e.printStackTrace();
        continue;
      }
      for (int i = 0; i < inputChannels; ++i) {
        for (int j = 0; j < sources; ++j) {
          try {
            if (patchbay.isConnected(u, j, module, i)) {
              addConnection(u, j, module, i);
            }
          } catch (RemoteException e) {
            e.printStackTrace();
          }
        }
      }
    }

    modules.add(module);

    if (notification == null) {
      notification =
          new Notification.Builder(getContext()).setContentTitle(module)
              .setSmallIcon(R.drawable.emo_im_happy).build();
    }
    addModuleView(module, inputChannels, outputChannels, notification);
  }

  public void deleteModule(String module) {
    for (Iterator<Entry<Pair<String, Integer>, List<Pair<String, Integer>>>> iter =
        connections.entrySet().iterator(); iter.hasNext();) {
      Entry<Pair<String, Integer>, List<Pair<String, Integer>>> entry = iter.next();
      if (entry.getKey().first.equals(module)) {
        iter.remove();
      } else {
        for (Iterator<Pair<String, Integer>> iter2 = entry.getValue().iterator(); iter2.hasNext();) {
          Pair<String, Integer> q = iter2.next();
          if (q.first.equals(module)) {
            iter2.remove();
          }
        }
      }
    }
    modules.remove(module);
    deleteModuleView(module);
  }

  public void activateModule(String module) {
    updateModuleView(module, true);
  }
  public void deactivateModule(String module) {
    updateModuleView(module, false);
  }

  public void addConnection(String source, int sourcePort, String sink, int sinkPort) {
    Pair<String, Integer> a = new Pair<String, Integer>(source, sourcePort);
    List<Pair<String, Integer>> c = connections.get(a);
    if (c == null) {
      c = new ArrayList<Pair<String, Integer>>();
      connections.put(a, c);
    }
    Pair<String, Integer> b = new Pair<String, Integer>(sink, sinkPort);
    if (!c.contains(b)) {
      c.add(b);
    }
    invalidateAll();
  }

  public void removeConnection(String source, int sourcePort, String sink, int sinkPort) {
    Pair<String, Integer> a = new Pair<String, Integer>(source, sourcePort);
    List<Pair<String, Integer>> c = connections.get(a);
    if (c == null) {
      return;
    }
    Pair<String, Integer> b = new Pair<String, Integer>(sink, sinkPort);
    c.remove(b);
    invalidateAll();
  }


  // --------------------------------------------------------------------------------
  // Crude, hacky GUI code below.
  // TODO: Implement this properly!!!

  private final Map<String, View> moduleViews = new HashMap<String, View>();
  private final Map<String, List<View>> inputPorts = new HashMap<String, List<View>>();
  private final Map<String, List<View>> outputPorts = new HashMap<String, List<View>>();
  private PatchOverlay overlay = null;
  private ToggleButton selectedButton = null;
  private String selectedModule = null;
  private int selectedInput = -1;;
  private int selectedOutput = -1;;

  private void addModuleView(final String module, int inputChannels, int outputChannels,
      final Notification notification) {
    if (overlay == null) {
      overlay = (PatchOverlay) findViewById(R.id.patchOverlay);
      overlay.setPatchView(this);
    }
    GridLayout moduleLayout = (GridLayout) findViewById(R.id.moduleGrid);
    View moduleView = inflate(getContext(), R.layout.module, null);
    // Warning: Atrocious hack to place view in the desired place, Part I.
    moduleLayout.addView(new Space(getContext()));
    moduleLayout.addView(new Space(getContext()));
    moduleLayout.addView(moduleView);
    moduleViews.put(module, moduleView);

    LinearLayout buttonLayout = (LinearLayout) moduleView.findViewById(R.id.inputPorts);
    List<View> buttons = new ArrayList<View>();
    inputPorts.put(module, buttons);
    for (int i = 0; i < inputChannels; ++i) {
      final ToggleButton button = new ToggleButton(getContext());
      buttons.add(button);
      button.setTextOn("In" + i);
      button.setTextOff("In" + i);
      button.setChecked(false);
      buttonLayout.addView(button);
      final int j = i;
      button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          if (isChecked) {
            if (selectedButton == null) {
              selectedButton = button;
              selectedModule = module;
              selectedInput = j;
              return;
            } else if (selectedOutput >= 0) {
              try {
                if (patchbay.isConnected(selectedModule, selectedOutput, module, j)) {
                  patchbay.disconnectModules(selectedModule, selectedOutput, module, j);
                } else {
                  patchbay.connectModules(selectedModule, selectedOutput, module, j);
                }
              } catch (RemoteException e) {
                e.printStackTrace();
              }
            }
          }
          button.setChecked(false);
          if (selectedButton != null) {
            selectedButton.setChecked(false);
            selectedButton = null;
            selectedModule = null;
            selectedInput = -1;
            selectedOutput = -1;
          }
        }
      });
    }

    FrameLayout frame = (FrameLayout) moduleView.findViewById(R.id.moduleFrame);
    View view = notification.contentView.apply(getContext(), frame);
    view.setOnLongClickListener(new OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        try {
          if (patchbay.isActive(module)) {
            patchbay.deactivateModule(module);
          } else {
            patchbay.activateModule(module);
          }
        } catch (RemoteException e) {
          e.printStackTrace();
        }
        return true;
      }
    });
    if (notification.contentIntent != null) {
      view.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          try {
            notification.contentIntent.send();
          } catch (CanceledException e) {
            e.printStackTrace();
          }
        }
      });
    }
    frame.addView(view);

    buttonLayout = (LinearLayout) moduleView.findViewById(R.id.outputPorts);
    buttons = new ArrayList<View>();
    outputPorts.put(module, buttons);
    for (int i = 0; i < outputChannels; ++i) {
      final ToggleButton button = new ToggleButton(getContext());
      buttons.add(button);
      button.setTextOn("Out" + i);
      button.setTextOff("Out" + i);
      button.setChecked(false);
      buttonLayout.addView(button);
      final int j = i;
      button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          if (isChecked) {
            if (selectedButton == null) {
              selectedButton = button;
              selectedModule = module;
              selectedOutput = j;
              return;
            } else if (selectedInput >= 0) {
              try {
                if (patchbay.isConnected(module, j, selectedModule, selectedInput)) {
                  patchbay.disconnectModules(module, j, selectedModule, selectedInput);
                } else {
                  patchbay.connectModules(module, j, selectedModule, selectedInput);
                }
              } catch (RemoteException e) {
                e.printStackTrace();
              }
            }
          }
          button.setChecked(false);
          if (selectedButton != null) {
            selectedButton.setChecked(false);
            selectedButton = null;
            selectedModule = null;
            selectedInput = -1;
            selectedOutput = -1;
          }
        }
      });
    }

    boolean isActive = false;
    try {
      isActive = patchbay.isActive(module);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    updateModuleView(module, isActive);
  }

  private void deleteModuleView(String module) {
    GridLayout moduleLayout = (GridLayout) findViewById(R.id.moduleGrid);
    View moduleView = moduleViews.remove(module);
    if (moduleView != null) {
      int index;
      for (index = 0; !moduleView.equals(moduleLayout.getChildAt(index)); ++index);
      moduleLayout.removeView(moduleView);
      // Warning: Atrocious hack to place view in the desired place, Part II.
      while (index > 0 && moduleLayout.getChildAt(--index) instanceof Space) {
        moduleLayout.removeViewAt(index);
      }
    }
    invalidateAll();
  }

  void drawOverlay(Canvas canvas) {
    int a[] = new int[4];
    int b[] = new int[4];
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setColor(Color.RED);
    paint.setStrokeWidth(12);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Cap.ROUND);
    Path path = new Path();
    for (Pair<String, Integer> p : connections.keySet()) {
      getCoordinates(outputPorts.get(p.first).get(p.second), a);
      int x0 = (a[0] + a[2]) / 2;
      int y0 = (a[1] + a[3]) / 2;
      for (Pair<String, Integer> q : connections.get(p)) {
        getCoordinates(inputPorts.get(q.first).get(q.second), b);
        int x1 = (b[0] + b[2]) / 2;
        int y1 = (b[1] + b[3]) / 2;
        path.moveTo(x0, y0);
        path.cubicTo((x0 + x1) / 2, y0, (x0 + x1) / 2, y1, x1, y1);
      }
    }
    canvas.drawPath(path, paint);
  }

  private void getCoordinates(View v, int coords[]) {
    int p[] = new int[2];
    v.getLocationOnScreen(p);
    coords[0] = p[0];
    coords[1] = p[1];
    getLocationOnScreen(p);
    coords[0] -= p[0];
    coords[1] -= p[1];
    coords[2] = coords[0] + v.getWidth();
    coords[3] = coords[1] + v.getHeight();
  }

  private void updateModuleView(String module, boolean isActive) {
    View moduleView = moduleViews.get(module);
    FrameLayout frame = (FrameLayout) moduleView.findViewById(R.id.moduleFrame);
    frame.setAlpha(isActive ? 1.0f : 0.3f);
    invalidateAll();
  }

  private void invalidateAll() {
    invalidate();
    if (overlay != null) {
      overlay.invalidate();
    }
  }
}
