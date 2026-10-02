CLASS zcl_test_mobile_poc DEFINITION PUBLIC FINAL CREATE PUBLIC.

  PUBLIC SECTION.
    INTERFACES z2ui5_if_app.

    DATA scanned TYPE string.

  PROTECTED SECTION.
    DATA client TYPE REF TO z2ui5_if_client.

    METHODS view_display.
    METHODS on_event.

  PRIVATE SECTION.
ENDCLASS.


CLASS zcl_test_mobile_poc IMPLEMENTATION.

  METHOD z2ui5_if_app~main.

    " Demo for the native shell bridge (see /PLAN.md). The scan button is
    " the framework's custom control z2ui5.cc.NativeBridgeScan: it calls the
    " shell's window.abap2ui5Native.scanBarcode( ) and hands the result back
    " as an ordinary event argument. In a plain browser it renders nothing -
    " showInBrowser = true keeps it visible here, so a press reports
    " "native shell not available" through OnError instead.

    me->client = client.
    IF client->check_on_init( ).
      view_display( ).
    ELSEIF client->check_on_navigated( ).
      view_display( ).
    ELSEIF client->check_on_event( ).
      on_event( ).
    ENDIF.

  ENDMETHOD.

  METHOD view_display.

    DATA(view) = z2ui5_cl_ui5_view_builder=>factory(
        )->ele( n = `View` ns = `mvc`
            )->a( n = `xmlns`       v = `sap.m`
            )->a( n = `xmlns:mvc`   v = `sap.ui.core.mvc`
            )->a( n = `xmlns:z2ui5` v = `z2ui5.cc`
            )->a( n = `displayBlock` v = `true`
            )->a( n = `height`       v = `100%`

            )->ele( `Page`
                )->a( n = `title` v = `abap2UI5 - Native Shell PoC`

                )->tag( n = `NativeBridgeScan` ns = `z2ui5`
                    )->a( n = `text`          v = `Scan Barcode`
                    )->a( n = `value`         v = client->_bind( scanned )
                    )->a( n = `showInBrowser` v = `true`
                    )->a( n = `OnScan`        v = client->_event( val = `SCANNED` arg = `${$parameters>/value}` )
                    )->a( n = `OnError`       v = client->_event( val = `SCAN_ERROR` arg = `${$parameters>/message}` )

                )->tag( `Input`
                    )->a( n = `value`    v = client->_bind( scanned )
                    )->a( n = `editable` v = `false` ).

    client->view_display( view->stringify( ) ).

  ENDMETHOD.

  METHOD on_event.

    CASE client->get_event( ).
      WHEN `SCANNED`.
        client->message_toast_display( |Scanned: { client->get_event_arg( ) }| ).
      WHEN `SCAN_ERROR`.
        client->message_toast_display( |Scan failed: { client->get_event_arg( ) }| ).
    ENDCASE.

  ENDMETHOD.

ENDCLASS.
